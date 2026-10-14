"""
Prints each SDK route's attempts from attempts.jsonl with the gap since the previous attempt, and
the gap the SDK should have waited. Usage: report.py <attempts.jsonl> [route regex]

Expected gaps model OperationRepo's per-operation backoff:
  - After the Nth retryable failure an operation waits N * 15s (opRepoDefaultFailRetryBackoff),
    capped at 1h. There is no jitter and HttpClient does not re-send on its own.
  - Retry-After is the minimum wait, capped at 1h. An unparsable value, or a 429 without one,
    counts as 60s.
  - The queue may wait up to opRepoExecutionInterval (5s) more if something else was enqueued
    during the backoff, so a few seconds of slack is allowed.
A foreground or network return ends the backoff early (but not Retry-After), so short gaps right
after those are fine.
"""

import json
import re
import sys
from collections import OrderedDict

BACKOFF_STEP = 15
MAX_DELAY = 3600
RETRY_AFTER_PARSE_FAIL = 60
QUEUE_SLACK = 6.0
CLOCK_SLACK = 1.0
NOT_RETRIED = (400, 401, 402, 403, 404, 409, 410)
EXECUTOR_ROUTE = re.compile(r"^apps/[^/]+/(users(/by/.*)?|subscriptions/.+|custom_events)$")
IGNORED_ROUTE = re.compile(r"/iams$")
ID_SEGMENT = re.compile(r"(by/[^/]+/|subscriptions/|identity/)[^/]+")


def route_key(entry):
    path = ID_SEGMENT.sub(r"\1:id", entry["path"])
    return f"{entry['method']} {path}"


def backoff_window(failures, status, retry_after):
    low = min(failures * BACKOFF_STEP, MAX_DELAY)
    if retry_after is None and status == 429:
        retry_after = RETRY_AFTER_PARSE_FAIL
    has_floor = retry_after is not None
    if has_floor:
        try:
            floor = min(max(float(retry_after), 0), MAX_DELAY)
        except ValueError:
            floor = RETRY_AFTER_PARSE_FAIL
        low = max(low, floor)
    return low, low + QUEUE_SLACK, has_floor


def main():
    path = sys.argv[1]
    route_filter = re.compile(sys.argv[2]) if len(sys.argv) > 2 else None
    groups = OrderedDict()
    with open(path) as f:
        for line in f:
            if line.strip():
                entry = json.loads(line)
                groups.setdefault(route_key(entry), []).append(entry)

    if not groups:
        print("No SDK requests recorded yet.")
        return

    for key, entries in groups.items():
        if route_filter and not route_filter.search(key):
            continue
        if not EXECUTOR_ROUTE.search(entries[0]["path"]) or IGNORED_ROUTE.search(entries[0]["path"]):
            continue
        print(f"\n{key}")
        print(f"  {'#':>3} {'time':>8} {'status':>6} {'body':>8} {'gap':>8} {'expected':>16}  note")
        failures = 0
        expected = None
        start = entries[0]["t"]
        for i, entry in enumerate(entries):
            gap = entry["t"] - entries[i - 1]["t"] if i else None
            note = ""
            if expected and gap is not None:
                low, high, kind = expected
                if gap < low - CLOCK_SLACK:
                    note = f"EARLY ({kind})"
                    if not kind.endswith("Retry-After"):
                        note += "; fine if right after a foreground or network return"
                elif gap > high + CLOCK_SLACK:
                    note = f"LATE ({kind})"
                else:
                    note = kind
            elif gap is not None:
                note = "new attempt"
            expected_text = f"{expected[0]:.1f}-{expected[1]:.1f}s" if expected else ""
            gap_text = f"{gap:.1f}s" if gap is not None else ""
            mark = "*" if entry["injected"] else " "
            print(f"  {i + 1:>3} {entry['t'] - start:>7.1f}s {entry['status']:>5}{mark} {entry.get('body', ''):>8} {gap_text:>8} {expected_text:>16}  {note}")

            status = entry["status"]
            if 200 <= status < 300 or status in NOT_RETRIED:
                failures = 0
                expected = None
            else:
                failures += 1
                low, high, has_floor = backoff_window(failures, status, entry.get("retry_after"))
                expected = (low, high, f"backoff #{failures}" + (", Retry-After" if has_floor else ""))
    print("\n* = failure injected by the proxy")


if __name__ == "__main__":
    main()
