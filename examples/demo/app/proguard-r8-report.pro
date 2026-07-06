# R8 keep-rules reporting for the consumer keep-rules diff gate.
#
# Why: the OneSignal library modules ship consumer keep rules (consumer-rules.pro) that protect
# members reached only via string-based reflection (e.g. GoogleApiClient.connect/disconnect,
# Model getters, service constructors). Those breakages are invisible at assemble time because R8
# only errors on missing classes, not on renamed/removed methods reached by reflection string.
# Emitting the seeds (members R8 kept) lets CI diff them against a baseline and fail when a
# previously-protected member silently stops being kept.
#
# -printseeds lists exactly which classes/members survived shrinking (the primary diff signal).
# -printconfiguration writes the fully-merged R8 configuration (human-readable secondary signal).
# Paths are relative to the module dir; both land under build/ (gitignored) and are consumed by
# scripts/r8-keep-check.sh after the minified release build.
-printseeds build/outputs/r8-report/seeds.txt
-printconfiguration build/outputs/r8-report/configuration.txt
