"""
mitmproxy addon that injects failures into OneSignal SDK requests so operation retries can be
observed in the demo app. Driven by `retry-test.sh`; see README.md.

Only requests carrying the SDK's `SDK-Version` header are touched, so the demo's own REST calls
pass through. Every SDK request is appended to attempts.jsonl for `retry-test.sh report`.

Rules are re-read whenever rules.json changes. Each rule:
  {"id": "<unique>", "route": "update-user", "method": "PATCH", "path": "<regex>", "status": 503,
   "retry_after": "30", "times": 3}
`status` 0 drops the connection (the SDK sees status 0, like being offline). `times` is how many
matching requests fail before the rule passes them through; null fails forever. A rule keeps its
remaining count across reloads, keyed by `id`.
"""

import hashlib
import json
import os
import re
import time

from mitmproxy import http

STATE_DIR = os.environ["RETRY_TEST_STATE_DIR"]
RULES_PATH = os.path.join(STATE_DIR, "rules.json")
ATTEMPTS_PATH = os.path.join(STATE_DIR, "attempts.jsonl")

API_PATH_PREFIX = re.compile(r"^/(api/v1/)?")


class FaultProxy:
    def __init__(self):
        self.rules_mtime = None
        self.rules = []
        self.remaining = {}

    def load_rules(self):
        try:
            mtime = os.stat(RULES_PATH).st_mtime_ns
        except FileNotFoundError:
            mtime = None
        if mtime == self.rules_mtime:
            return
        self.rules_mtime = mtime
        self.rules = []
        if mtime is not None:
            with open(RULES_PATH) as f:
                self.rules = json.load(f)
        self.remaining = {rule["id"]: self.remaining.get(rule["id"], rule.get("times")) for rule in self.rules}
        print(f"[fault] rules: {json.dumps(self.rules) if self.rules else 'none, passing everything'}")

    def match(self, flow):
        path = API_PATH_PREFIX.sub("", flow.request.path.split("?")[0])
        for rule in self.rules:
            if rule.get("method") and rule["method"] != flow.request.method:
                continue
            if not re.search(rule["path"], path):
                continue
            remaining = self.remaining[rule["id"]]
            if remaining is not None:
                if remaining <= 0:
                    continue
                self.remaining[rule["id"]] = remaining - 1
            return rule
        return None

    def request(self, flow: http.HTTPFlow):
        if "onesignal.com" not in flow.request.pretty_host or "SDK-Version" not in flow.request.headers:
            return
        # Timed at the request, so gaps are between sends rather than between responses.
        flow.metadata["fault_sent_at"] = time.time()
        self.load_rules()
        rule = self.match(flow)
        flow.metadata["fault_rule"] = rule
        if rule is None:
            return
        if rule["status"] == 0:
            self.record(flow, 0, rule)
            flow.kill()
            return
        headers = {"Content-Type": "application/json"}
        if rule.get("retry_after") is not None:
            headers["Retry-After"] = str(rule["retry_after"])
        body = json.dumps({"errors": [{"title": f"Injected by fault_proxy ({rule['route']})"}]})
        flow.response = http.Response.make(rule["status"], body, headers)

    def response(self, flow: http.HTTPFlow):
        if "fault_rule" not in flow.metadata:
            return
        self.record(flow, flow.response.status_code, flow.metadata["fault_rule"])

    def error(self, flow: http.HTTPFlow):
        if "fault_rule" in flow.metadata and flow.metadata["fault_rule"] is None:
            self.record(flow, 0, None)

    def record(self, flow, status, rule):
        path = API_PATH_PREFIX.sub("", flow.request.path.split("?")[0])
        retry_after = flow.response.headers.get("Retry-After") if flow.response else None
        entry = {
            "t": flow.metadata["fault_sent_at"],
            "method": flow.request.method,
            "path": path,
            "status": status,
            "injected": rule is not None,
            "retry_after": retry_after,
            "body": hashlib.sha1(flow.request.raw_content or b"").hexdigest()[:8],
            "bearer": flow.request.headers.get("Authorization", "").startswith("Bearer "),
        }
        with open(ATTEMPTS_PATH, "a") as f:
            f.write(json.dumps(entry) + "\n")
        tag = "INJECTED" if rule else "passed"
        stamp = time.strftime("%H:%M:%S", time.localtime(entry["t"]))
        print(f"[fault] {stamp} {status:>3} {tag:<8} {flow.request.method:<6} {path} body={entry['body']}", flush=True)


addons = [FaultProxy()]
