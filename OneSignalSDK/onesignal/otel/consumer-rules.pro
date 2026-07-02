# OpenTelemetry OTLP exporter references Jackson core classes that are optional on Android.
# Suppress R8 missing-class errors when apps don't include jackson-core.
-dontwarn com.fasterxml.jackson.core.**

# OTel (e.g. sdk-logs AutoValue-generated types) references Google Auto Value annotations that are
# not on the app classpath. Wildcard covers inner types and extensions (e.g. Memoized).
-dontwarn com.google.auto.value.**

# Another SDK in the app (e.g. one pulling a newer opentelemetry-bom) can upgrade OneSignal's
# transitive OTel artifacts to a version where internal classes have moved or been removed, while
# the transitively-pulled -alpha "incubator" artifact still references the old internals (e.g.
# io.opentelemetry.api.internal.ApiUsageLogger, referenced by ExtendedDefaultTracer in
# opentelemetry-api-incubator). That cross-version skew surfaces as fatal R8 "Missing class" errors
# in the consuming app even though the code path is never executed. Scope the suppression to the
# unused incubator artifact (the source of the dangling reference) so R8 still warns about
# OneSignal's own actively-used OTel classes (api.logs, sdk, exporter) if a bump breaks them.
-dontwarn io.opentelemetry.api.incubator.**
