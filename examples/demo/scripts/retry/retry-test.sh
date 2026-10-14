#!/usr/bin/env bash
# Fault-injection harness for manually testing SDK operation retries in the demo app.
# Run `./retry-test.sh help` or see README.md.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_DIR="$(cd "$SCRIPT_DIR/../../../../OneSignalSDK" && pwd)"
STATE_DIR="${RETRY_TEST_STATE_DIR:-$SCRIPT_DIR/.state}"
RULES="$STATE_DIR/rules.json"
ATTEMPTS="$STATE_DIR/attempts.jsonl"
PROXY_LOG="$STATE_DIR/proxy.log"
PID_FILE="$STATE_DIR/proxy.pid"
PORT="${RETRY_TEST_PORT:-8899}"
PACKAGE="${RETRY_TEST_PACKAGE:-com.onesignal.example}"
CA_CERT="$HOME/.mitmproxy/mitmproxy-ca-cert.pem"

export RETRY_TEST_STATE_DIR="$STATE_DIR"
mkdir -p "$STATE_DIR"

die() { echo "error: $*" >&2; exit 1; }

# Sets SERIAL from RETRY_TEST_SERIAL / ANDROID_SERIAL, or the only connected device.
resolve_device() {
  command -v adb >/dev/null || die "adb not found. Add \$ANDROID_HOME/platform-tools to PATH."
  SERIAL="${RETRY_TEST_SERIAL:-${ANDROID_SERIAL:-}}"
  if [[ -z "$SERIAL" ]]; then
    local devices
    devices="$(adb devices | awk 'NR > 1 && $2 == "device" {print $1}')"
    [[ -n "$devices" ]] || die "no device connected. Start an emulator or plug in a device."
    [[ "$(wc -l <<< "$devices" | tr -d ' ')" == "1" ]] \
      || die "several devices connected ($(echo $devices)). Pick one with RETRY_TEST_SERIAL=<serial>."
    SERIAL="$devices"
  fi
}

dev() { adb -s "$SERIAL" "$@"; }

is_emulator() { [[ "$SERIAL" == emulator-* ]]; }

launch_app() { dev shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; }

route_rule() {
  # Prints "<method> <path regex>" for a route name. Paths are relative to https://api.onesignal.com/.
  case "$1" in
    create-user)     echo "POST ^apps/[^/]+/users$" ;;
    fetch-user)      echo "GET ^apps/[^/]+/users/by/[^/]+/[^/]+$" ;;
    update-user)     echo "PATCH ^apps/[^/]+/users/by/[^/]+/[^/]+$" ;;
    identity)        echo "PATCH /identity$" ;;
    remove-alias)    echo "DELETE /identity/[^/]+$" ;;
    create-sub)      echo "POST /subscriptions$" ;;
    update-sub)      echo "PATCH ^apps/[^/]+/subscriptions/[^/]+$" ;;
    delete-sub)      echo "DELETE ^apps/[^/]+/subscriptions/[^/]+$" ;;
    transfer-sub)    echo "PATCH /subscriptions/[^/]+/owner$" ;;
    identity-by-sub) echo "GET /user/identity$" ;;
    custom-events)   echo "POST /custom_events$" ;;
    all)             echo "- ." ;;
    *) die "unknown route '$1'. Routes: create-user fetch-user update-user identity remove-alias create-sub update-sub delete-sub transfer-sub identity-by-sub custom-events all" ;;
  esac
}

proxy_running() { [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; }

set_device_proxy() {
  # An emulator reaches the Mac at 10.0.2.2 through its emulated network, so turning the
  # emulator's network off really takes it offline. A physical device goes through `adb reverse`.
  local host="10.0.2.2"
  if ! is_emulator; then
    dev reverse "tcp:$PORT" "tcp:$PORT" >/dev/null
    host="127.0.0.1"
  fi
  dev shell settings put global http_proxy "$host:$PORT"
  echo "Pointed $SERIAL's global HTTP proxy at $host:$PORT (cleared by 'stop')."
}

clear_device_proxy() {
  dev shell settings put global http_proxy :0
  dev reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true
  echo "Cleared $SERIAL's HTTP proxy."
}

ensure_ca() {
  [[ -f "$CA_CERT" ]] && return
  command -v mitmdump >/dev/null || die "mitmdump not found. Install with: brew install mitmproxy"
  # mitmproxy writes its CA on first run.
  mitmdump --listen-port 0 >/dev/null 2>&1 &
  local pid=$!
  for _ in $(seq 1 50); do [[ -f "$CA_CERT" ]] && break; sleep 0.1; done
  kill "$pid" 2>/dev/null || true
  [[ -f "$CA_CERT" ]] || die "mitmproxy CA not generated at $CA_CERT"
}

cmd_start() {
  command -v mitmdump >/dev/null || die "mitmdump not found. Install with: brew install mitmproxy"
  proxy_running && die "proxy already running (pid $(cat "$PID_FILE")). Run '$0 stop' first."
  resolve_device
  : > "$ATTEMPTS"
  [[ -f "$RULES" ]] || echo "[]" > "$RULES"
  # Only onesignal.com is decrypted; everything else is tunnelled untouched.
  # Loopback is enough: an emulator's 10.0.2.2 and `adb reverse` both land on it.
  mitmdump --listen-host 127.0.0.1 --listen-port "$PORT" \
    --allow-hosts '^(.+\.)?onesignal\.com(:443)?$' \
    --set connection_strategy=lazy \
    --set termlog_verbosity=warn \
    -s "$SCRIPT_DIR/fault_proxy.py" > "$PROXY_LOG" 2>&1 &
  echo $! > "$PID_FILE"
  sleep 1
  proxy_running || { cat "$PROXY_LOG"; die "mitmdump exited"; }
  echo "mitmdump running (pid $(cat "$PID_FILE")), log: $PROXY_LOG"
  set_device_proxy
  echo "Relaunch the demo app so it picks up the proxy: $0 relaunch"
}

cmd_stop() {
  if proxy_running; then
    kill "$(cat "$PID_FILE")" && echo "Stopped mitmdump."
  fi
  rm -f "$PID_FILE"
  resolve_device
  clear_device_proxy
}

write_rules() {
  # write_rules <json array>; written atomically so the addon never reads a partial file.
  echo "$1" > "$RULES.tmp" && mv "$RULES.tmp" "$RULES"
}

cmd_fail() {
  local route="${1:-}" status="${2:-}"
  [[ -n "$route" && -n "$status" ]] || die "usage: $0 fail <route> <status|0> [--times N] [--retry-after S] [--add]"
  shift 2
  local times="null" retry_after="null" add=false
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --times) times="$2"; shift 2 ;;
      --retry-after) retry_after="\"$2\""; shift 2 ;;
      --add) add=true; shift ;;
      *) die "unknown option $1" ;;
    esac
  done
  [[ "$status" == "0" && "$retry_after" != "null" ]] && die "--retry-after needs an HTTP status; a dropped connection has no headers"
  local method path
  # A die inside $(...) only exits the subshell, so check it here or a typo becomes a match-all rule.
  local spec
  spec="$(route_rule "$route")" || exit 1
  read -r method path <<< "$spec"
  [[ "$method" == "-" ]] && method=""
  python3 - "$RULES" "$add" "$route" "$method" "$path" "$status" "$times" "$retry_after" > "$RULES.tmp" <<'PY'
import json, sys, uuid
rules_path, add, route, method, path, status, times, retry_after = sys.argv[1:]
rules = json.load(open(rules_path)) if add == "true" else []
rules.append({"id": uuid.uuid4().hex[:8], "route": route, "method": method or None, "path": path,
              "status": int(status), "times": json.loads(times), "retry_after": json.loads(retry_after)})
print(json.dumps(rules))
PY
  mv "$RULES.tmp" "$RULES"
  echo "Rules now: $(cat "$RULES")"
}

cmd_clear() {
  write_rules "[]"
  echo "Cleared all rules; requests pass through."
}

cmd_reset_log() { : > "$ATTEMPTS"; echo "Cleared recorded attempts."; }

restore_radios() {
  # wifi_on is 2 when Wi-Fi is on in airplane mode, so anything but 0 counts as on.
  local ok=true
  if [[ "$WIFI_WAS_ON" != "0" ]]; then dev shell svc wifi enable || ok=false; fi
  if [[ "$DATA_WAS_ON" != "0" ]]; then dev shell svc data enable || ok=false; fi
  if $ok; then echo "Network restored."; else echo "error: could not turn the network back on; check $SERIAL" >&2; fi
}

cmd_network_flap() {
  # Fires the SDK's online callback when the network comes back. On an emulator this also takes
  # the proxy route offline; through `adb reverse` a physical device can still reach the proxy.
  local seconds="${1:-10}"
  resolve_device
  [[ "$SERIAL" == *:* || "$SERIAL" == *._adb-tls-connect._tcp* ]] \
    && die "$SERIAL is wireless adb, which drops with Wi-Fi. Use USB or an emulator."
  WIFI_WAS_ON="$(dev shell settings get global wifi_on | tr -d '\r')"
  DATA_WAS_ON="$(dev shell settings get global mobile_data | tr -d '\r')"
  # Only turns back on what was on, even after Ctrl-C or a failed `svc`.
  trap restore_radios EXIT
  trap 'exit 130' INT TERM
  echo "Wi-Fi and mobile data off on $SERIAL for ${seconds}s..."
  dev shell svc wifi disable
  dev shell svc data disable
  # Waited on in the background so a SIGTERM runs the trap right away.
  sleep "$seconds" & wait $!
}

cmd_fresh() {
  # `pm clear` makes the SDK start as a new install (Create User and so on). Granting the
  # notification permission again keeps the prompt from covering the demo.
  resolve_device
  dev shell pm path "$PACKAGE" >/dev/null 2>&1 || die "demo app not installed on $SERIAL. Run '$0 install'."
  dev shell pm clear "$PACKAGE" >/dev/null
  dev shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
  echo "Cleared demo app data."
  launch_app
}

cmd_install() {
  # Builds the demo against the local SDK source, trusting the mitmproxy CA. SDK_VERSION is
  # required by OneSignalSDK/ but only labels the build.
  resolve_device
  ensure_ca
  (cd "$SDK_DIR" && ANDROID_SERIAL="$SERIAL" ./gradlew -q :app:installGmsDebug \
    -PSDK_VERSION="${SDK_VERSION:-0.0.0-retry-test}" -PRETRY_TEST_CA="$CA_CERT")
  dev shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
  # The stylus handwriting prompt on newer emulators swallows Maestro's text input.
  dev shell settings put secure stylus_handwriting_enabled 0
  echo "Installed the demo app on $SERIAL. Next: $0 start, then $0 fresh"
}

cmd_tap() {
  # Drives the demo's UI through Maestro flows in flows/, using the demo's test tags.
  command -v maestro >/dev/null || die "maestro not found. Install with: brew install mobile-dev-inc/tap/maestro"
  local action="${1:-}" env=()
  case "$action" in
    add-tag|add-alias) [[ $# -eq 3 ]] || die "usage: $0 tap $action <key> <value>"; env=(-e "KEY=$2" -e "VALUE=$3") ;;
    add-email|login) [[ $# -eq 2 ]] || die "usage: $0 tap $action <value>"; env=(-e "VALUE=$2") ;;
    logout|toggle-push|allow-notifications) ;;
    *) die "usage: $0 tap <add-tag|add-alias|add-email|login|logout|toggle-push|allow-notifications> [args]" ;;
  esac
  resolve_device
  # The grep only trims output; with pipefail a failed flow still fails `tap`.
  local status=0
  MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    maestro --device "$SERIAL" test ${env[@]+"${env[@]}"} "$SCRIPT_DIR/flows/$action.yaml" \
    | { grep -E '^ *(>|[A-Z].*(COMPLETED|FAILED|WARNED))' || true; } || status=$?
  [[ "$status" == "0" ]] || die "maestro flow '$action' failed (exit $status)"
}

cmd_logs() {
  resolve_device
  local pid
  pid="$(dev shell pidof "$PACKAGE" | tr -d '\r')"
  [[ -n "$pid" ]] || die "demo app is not running"
  dev logcat -v time --pid="$pid" | grep --line-buffered -iE 'OperationRepo|HttpClient|retry|retry-after'
}

usage() {
  cat <<EOF
Usage: $0 <command>

Proxy
  start                     Start mitmproxy and point the device's HTTP proxy at it
  stop                      Stop mitmproxy and clear the device's proxy

Faults (take effect immediately, no relaunch needed)
  fail <route> <status> [--times N] [--retry-after S] [--add]
                            Fail matching SDK requests. Status 0 drops the connection.
                            --times N: fail N times, then pass. Default: until cleared.
                            --add: keep existing rules instead of replacing them.
  clear                     Remove all rules
  rules                     Show current rules

Observe
  watch                     Follow every SDK request as it passes the proxy
  report [route regex]      Gaps between attempts per route vs. the expected backoff
  reset-log                 Forget recorded attempts
  logs                      Stream the demo app's OperationRepo / HttpClient logs

App / environment
  install                   Build the demo against the local SDK, trusting the mitmproxy CA, and install it
  tap <action> [args]       Drive the demo's UI with Maestro:
                              add-tag <key> <value>, add-alias <label> <id>, add-email <email>,
                              login <external id>, logout, toggle-push, allow-notifications
  relaunch                  Kill and relaunch the demo app
  fresh                     Clear the demo app's data (new SDK user) and relaunch
  background | foreground   Send the demo app to the background, or bring it back
  network-flap [seconds]    Turn the device's Wi-Fi and mobile data off and back on (default 10s)

Routes: create-user fetch-user update-user identity remove-alias create-sub update-sub
        delete-sub transfer-sub identity-by-sub custom-events all
Env: RETRY_TEST_SERIAL (adb serial), RETRY_TEST_PORT ($PORT), RETRY_TEST_PACKAGE ($PACKAGE)
EOF
}

case "${1:-help}" in
  start) cmd_start ;;
  stop) cmd_stop ;;
  fail) shift; cmd_fail "$@" ;;
  clear) cmd_clear ;;
  rules) cat "$RULES" 2>/dev/null || echo "[]" ;;
  watch) tail -n +1 -F "$PROXY_LOG" | grep --line-buffered '^\[fault\]' ;;
  report) shift; python3 "$SCRIPT_DIR/report.py" "$ATTEMPTS" "$@" ;;
  reset-log) cmd_reset_log ;;
  logs) cmd_logs ;;
  fresh) cmd_fresh ;;
  install) cmd_install ;;
  tap) shift; cmd_tap "$@" ;;
  relaunch) resolve_device; dev shell am force-stop "$PACKAGE"; launch_app ;;
  background) resolve_device; dev shell input keyevent KEYCODE_HOME && echo "Demo app backgrounded." ;;
  foreground) resolve_device; launch_app && echo "Demo app foregrounded." ;;
  network-flap) shift; cmd_network_flap "$@" ;;
  help|-h|--help) usage ;;
  *) usage; exit 1 ;;
esac
