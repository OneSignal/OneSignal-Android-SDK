# OpenTelemetry OTLP exporter references Jackson core classes that are optional on Android.
# Suppress R8 missing-class errors when apps don't include jackson-core.
-dontwarn com.fasterxml.jackson.core.**

# OTel (e.g. sdk-logs AutoValue-generated types) references Google Auto Value annotations that are
# not on the app classpath. Wildcard covers inner types and extensions (e.g. Memoized).
-dontwarn com.google.auto.value.**

# Suppress R8 missing-class errors from OpenTelemetry version skew. When a host app bumps the
# transitive opentelemetry-bom, removed internal classes (e.g. io.opentelemetry.api.internal.ApiUsageLogger)
# leave dangling references from the unused opentelemetry-api-incubator alpha (ExtendedDefaultTracer).
# R8 suppresses a "Missing class" diagnostic when the MISSING class matches -dontwarn, so match the
# io.opentelemetry.api.internal package directly (referrer-independent). Scoped to api.internal (not a
# broad **.internal.** wildcard) so genuine missing-class errors in the sdk/exporter internals that
# OneSignal actively uses still surface. The incubator rule covers the unused ExtendedDefaultTracer path.
-dontwarn io.opentelemetry.api.incubator.**
-dontwarn io.opentelemetry.api.internal.**
