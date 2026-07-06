#!/usr/bin/env bash
#
# R8 consumer keep-rules diff gate.
#
# Why this exists: the OneSignal library modules ship consumer keep rules
# (OneSignalSDK/onesignal/*/consumer-rules.pro) that protect members reached ONLY via
# string-based reflection -- e.g. GoogleApiClient.connect/disconnect/blockingConnect (invoked
# by name from GoogleApiClientCompatProxy), Model getters (matched by name in
# Model.initializeFromJson), and service constructors (ServiceRegistrationReflection).
# Dropping or mis-scoping such a keep does NOT fail the build: R8 only errors on missing
# classes, not on a method it renamed/removed that is later looked up by a reflection string.
# The breakage surfaces only at runtime. This gate makes the demo's minified release R8 run
# emit its seeds (the members R8 actually kept), normalizes them to a stable set, and diffs
# that against a checked-in baseline so a silently-dropped keep fails the PR instead.
#
# Usage:
#   scripts/r8-keep-check.sh                 # compare against baseline, fail on drift
#   UPDATE_R8_BASELINE=1 scripts/r8-keep-check.sh   # accept current seeds as the new baseline
#
# Run it AFTER a minified GMS release build (:app:assembleGmsRelease) so the seeds file exists.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# GMS release is the variant we diff: it includes Google Play services (so GoogleApiClient is on
# the classpath) plus every OneSignal module. The Huawei variant excludes play-services, so its
# seeds legitimately lack the GMS keeps and are not a stable baseline for them.
SEEDS_FILE="$REPO_ROOT/examples/demo/app/build/outputs/r8-report/seeds.txt"
BASELINE_FILE="$REPO_ROOT/examples/demo/app/r8-keep-baseline.txt"

RED=$'\033[0;31m'; GREEN=$'\033[0;32m'; YELLOW=$'\033[1;33m'; NC=$'\033[0m'

if [[ ! -f "$SEEDS_FILE" ]]; then
  echo "${RED}ERROR:${NC} seeds file not found at:"
  echo "  $SEEDS_FILE"
  echo "Run the minified GMS release build first, e.g.:"
  echo "  (cd OneSignalSDK && ./gradlew :app:assembleGmsRelease)"
  exit 2
fi

# Third-party classes we keep reflectively are derived from the consumer rules rather than
# hardcoded, so a new explicit third-party -keep target is picked up automatically. We take the
# concrete class token from '-keep[classmembers] class <FQN> {' lines, excluding wildcards and our
# own com.onesignal.* packages (those are covered by the com.onesignal prefix below).
# '|| true' so an empty result (e.g. every explicit third-party keep was removed) doesn't abort
# under 'set -e' -- that removal is exactly the drift the gate must still report on.
THIRD_PARTY_PREFIXES="$(
  { grep -rhE '^[[:space:]]*-keep(classmembers|classeswithmembers)?\b' \
      "$REPO_ROOT"/OneSignalSDK/onesignal/*/consumer-rules.pro 2>/dev/null \
    | sed -nE 's/.*class[[:space:]]+([A-Za-z_][A-Za-z0-9_.]*)([[:space:]{].*)?$/\1/p' \
    | grep '\.' \
    | grep -v '^com\.onesignal' \
    | sort -u ; } || true
)"

# Normalize the raw seeds into the stable, high-signal set we diff:
#  - member-level seeds only (lines with ": ") -- these are the constructors / getters / enum
#    constants / methods that keep rules protect; a dropped keep shows up as a missing member here.
#  - owned by com.onesignal.* (the SDK) or one of the reflectively-kept third-party classes.
#  - excluding com.onesignal.example.* (the demo app's own code, not the SDK under test).
#  - excluding anonymous/lambda owners (a '$' followed by a digit) whose R8 numbering churns on
#    unrelated code changes and would make the baseline noisy.
normalize() {
  awk -v tp="$THIRD_PARTY_PREFIXES" '
    BEGIN { n = split(tp, arr, "\n") }
    index($0, ": ") == 0 { next }
    {
      owner = $0; sub(/: .*/, "", owner)
      if (owner ~ /^com\.onesignal\.example/) next
      if (owner ~ /\$[0-9]/) next
      keep = 0
      if (owner ~ /^com\.onesignal\./) keep = 1
      else { for (i = 1; i <= n; i++) if (arr[i] != "" && index(owner, arr[i]) == 1) { keep = 1; break } }
      if (keep) print
    }
  ' "$SEEDS_FILE" | LC_ALL=C sort -u
}

CURRENT="$(normalize)"

if [[ "${UPDATE_R8_BASELINE:-0}" == "1" ]]; then
  printf '%s\n' "$CURRENT" > "$BASELINE_FILE"
  echo "${GREEN}Updated baseline${NC} ($(printf '%s\n' "$CURRENT" | wc -l | tr -d ' ') kept members):"
  echo "  $BASELINE_FILE"
  exit 0
fi

if [[ ! -f "$BASELINE_FILE" ]]; then
  echo "${RED}ERROR:${NC} baseline not found at:"
  echo "  $BASELINE_FILE"
  echo "Generate it once from the current (intended) state:"
  echo "  UPDATE_R8_BASELINE=1 scripts/r8-keep-check.sh"
  exit 2
fi

DIFF="$(diff -u "$BASELINE_FILE" <(printf '%s\n' "$CURRENT") || true)"

if [[ -z "$DIFF" ]]; then
  echo "${GREEN}R8 keep-rules gate passed${NC} — kept consumer members match the baseline ($(wc -l < "$BASELINE_FILE" | tr -d ' ') entries)."
  exit 0
fi

REMOVED="$(printf '%s\n' "$DIFF" | grep -E '^-[^-]' | sed 's/^-/  - /' || true)"
ADDED="$(printf '%s\n' "$DIFF" | grep -E '^\+[^+]' | sed 's/^+/  + /' || true)"

echo "${RED}R8 keep-rules gate FAILED — the set of kept consumer members drifted from the baseline.${NC}"
echo
if [[ -n "$REMOVED" ]]; then
  echo "${RED}Members NO LONGER kept (a keep rule may have been dropped or mis-scoped —"
  echo "verify none of these are reached by reflection before accepting):${NC}"
  echo "$REMOVED"
  echo
fi
if [[ -n "$ADDED" ]]; then
  echo "${YELLOW}Newly kept members (expected when adding rules or SDK code):${NC}"
  echo "$ADDED"
  echo
fi
echo "If this change is intentional (you added/changed a keep rule or SDK surface), refresh the"
echo "baseline and include it in your commit:"
echo "  ${YELLOW}UPDATE_R8_BASELINE=1 scripts/r8-keep-check.sh${NC}"
exit 1
