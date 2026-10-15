"""
Prints each SDK operation's attempts from attempts.jsonl with the gap since the previous attempt,
and the gap the SDK should have waited. Usage: report.py <attempts.jsonl> [route regex]

Expected gaps model OperationRepo's backoff:
  - After the Nth retryable failure a request waits 5s * 2^(N-1), capped at 300s, times a jitter
    of 0.5 to 1. HttpClient does not re-send on its own.
  - Retry-After is the minimum wait, capped at 1h. An unparsable value, or a 429 without one,
    counts as 60s. HttpClient also holds every SDK request until it passes, which is checked
    separately at the end.
  - A timed retry skips the queue's batching wait, but an enqueue near the end of a backoff can add
    up to opRepoExecutionInterval (5s), so that much slack is allowed.
A foreground, or a network return while in the foreground, ends the backoff early (but not
Retry-After), so short gaps right after those are fine.
Success is 200, 201, 202 and 304, as in HttpResponse.isSuccess. 400, 402, 409 and 410 end a
request, so the same body sent again is unexpected. A 404 is scored as a backoff, since most
executors retry it (inside the new-record window, or by requeueing for the current user); login
and custom events stop on it. A 401 or 403 on a request signed with a Bearer
token holds it, with its retry count, until a new JWT is set (OperationRepo.handleFailUnauthorized);
an unsigned one ends it. After a hold, a different body is taken as a new request and resets the
count. That is a guess from the body hash: a held operation batched with newer ones comes back
with a new body and keeps its count in the SDK.
"""

import json
import math
import re
import sys
from collections import OrderedDict

BASE_BACKOFF = 5
MAX_BACKOFF = 300
MIN_JITTER = 0.5
MAX_RETRY_AFTER = 3600
RETRY_AFTER_PARSE_FAIL = 60
QUEUE_SLACK = 5.0
CLOCK_SLACK = 1.0
SUCCESS = (200, 201, 202, 304)
NOT_RETRIED = (400, 401, 402, 403, 409, 410)
HELD = (401, 403)


def is_hold(entry):
    return entry["status"] in HELD and bool(entry.get("bearer"))
EXECUTOR_ROUTE = re.compile(r"^apps/[^/]+/(users(/by/.*)?|subscriptions/.+|custom_events)$")
IGNORED_ROUTE = re.compile(r"/iams$")
# Routes without a record id in the path. Their attempts are told apart by body instead.
NO_ID_ROUTE = re.compile(r"(/users|/subscriptions|/custom_events)$")
# Remote logging is sent by the shared KMP logger, not HttpClient, so the gate doesn't apply.
NOT_GATED_ROUTE = re.compile(r"^sdk/log$")


def operation_key(entry):
    key = f"{entry['method']} {entry['path']}"
    if entry["method"] == "POST" and NO_ID_ROUTE.search(entry["path"]):
        key += f" body={entry.get('body', '')}"
    return key


def retry_after_seconds(status, header):
    """Mirrors HttpClient.retryAfterFromResponse."""
    if header is None:
        return RETRY_AFTER_PARSE_FAIL if status == 429 else None
    try:
        value = float(str(header).strip())
    except ValueError:
        return RETRY_AFTER_PARSE_FAIL
    if not math.isfinite(value) or value < 0:
        return RETRY_AFTER_PARSE_FAIL
    return math.ceil(min(value, MAX_RETRY_AFTER))


def backoff_window(failures, retry_after):
    backoff = min(BASE_BACKOFF * 2 ** (failures - 1), MAX_BACKOFF)
    low, high = backoff * MIN_JITTER, backoff
    if retry_after is not None:
        low, high = max(low, retry_after), max(high, retry_after)
    return low, high + QUEUE_SLACK


def print_operation(key, entries):
    print(f"\n{key}")
    print(f"  {'#':>3} {'time':>8} {'status':>6} {'body':>8} {'gap':>8} {'expected':>16}  note")
    failures = 0
    expected = None
    ended = None
    start = entries[0]["t"]
    for i, entry in enumerate(entries):
        gap = entry["t"] - entries[i - 1]["t"] if i else None
        note = ""
        if expected and gap is not None:
            low, high, kind = expected
            if gap < low:
                note = f"EARLY ({kind})"
                if not kind.endswith("Retry-After"):
                    note += "; fine if right after a foreground or network return"
            elif gap > high + CLOCK_SLACK:
                note = f"LATE ({kind})"
            else:
                note = kind
        elif ended and is_hold(ended) and entry.get("body") == ended.get("body"):
            note = f"resend after held {ended['status']}; fine once a new JWT is set"
        elif ended and entry.get("body") == ended.get("body"):
            note = f"UNEXPECTED resend after {ended['status']}"
        elif ended:
            note = f"new request after {ended['status']}"
        elif gap is not None:
            note = "new attempt"
        expected_text = f"{expected[0]:.1f}-{expected[1]:.1f}s" if expected else ""
        gap_text = f"{gap:.1f}s" if gap is not None else ""
        mark = "*" if entry["injected"] else " "
        print(f"  {i + 1:>3} {entry['t'] - start:>7.1f}s {entry['status']:>5}{mark} {entry.get('body', ''):>8} {gap_text:>8} {expected_text:>16}  {note}")

        status = entry["status"]
        if ended and is_hold(ended) and entry.get("body") != ended.get("body"):
            failures = 0
        ended = entry if status in NOT_RETRIED else None
        if is_hold(entry):
            expected = None
        elif status in SUCCESS or status in NOT_RETRIED:
            failures = 0
            expected = None
        else:
            failures += 1
            retry_after = retry_after_seconds(status, entry.get("retry_after"))
            low, high = backoff_window(failures, retry_after)
            expected = (low, high, f"backoff #{failures}" + (", Retry-After" if retry_after is not None else ""))


def print_gate_violations(entries):
    """HttpClient holds every SDK request, on any route, until a Retry-After passes."""
    windows = []
    for entry in entries:
        retry_after = retry_after_seconds(entry["status"], entry.get("retry_after"))
        if retry_after:
            windows.append((entry["t"], entry["t"] + retry_after))
    violations = [
        entry
        for entry in entries
        if not NOT_GATED_ROUTE.search(entry["path"])
        and any(start < entry["t"] < end for start, end in windows)
    ]
    if not windows:
        return
    if not violations:
        print("\nNo SDK request was sent during a Retry-After.")
        return
    print("\nSENT DURING A RETRY-AFTER (HttpClient should have held these):")
    for entry in violations:
        print(f"  {entry['method']} {entry['path']} status={entry['status']} body={entry.get('body', '')}")


def main():
    path = sys.argv[1]
    route_filter = re.compile(sys.argv[2]) if len(sys.argv) > 2 else None
    entries = []
    with open(path) as f:
        for line in f:
            if line.strip():
                try:
                    entries.append(json.loads(line))
                except json.JSONDecodeError:
                    # The proxy may be mid-write on the last line.
                    continue

    if not entries:
        print("No SDK requests recorded yet.")
        return

    operations = OrderedDict()
    for entry in entries:
        if EXECUTOR_ROUTE.search(entry["path"]) and not IGNORED_ROUTE.search(entry["path"]):
            operations.setdefault(operation_key(entry), []).append(entry)

    for key, op_entries in operations.items():
        if not route_filter or route_filter.search(key):
            print_operation(key, op_entries)
    print_gate_violations(entries)
    print("\n* = failure injected by the proxy")


if __name__ == "__main__":
    main()
