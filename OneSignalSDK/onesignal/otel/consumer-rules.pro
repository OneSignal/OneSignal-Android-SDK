# OpenTelemetry OTLP exporter references Jackson classes that are optional on Android.
# These match the MISSING jackson/autovalue types (not io.opentelemetry), so they do not
# hide OpenTelemetry diagnostics from other libraries in the host app.
-dontwarn com.fasterxml.jackson.**

# OTel (e.g. sdk-logs AutoValue-generated types) references Google Auto Value annotations that are
# not on the app classpath. Wildcard covers inner types and extensions (e.g. Memoized).
-dontwarn com.google.auto.value.**

# OpenTelemetry itself is relocated into com.onesignal.shaded.opentelemetry and embedded in this
# AAR (SDK-5006 / #2714). Do NOT add -dontwarn io.opentelemetry.** here: consumer rules are merged
# into the host app's R8 config and would suppress missing-class errors for every other library
# that uses OpenTelemetry (e.g. Embrace). Matching the relocated package keeps remaining
# optional-path suppressions (incubator config, unused exporters) OneSignal-private.
-keep class com.onesignal.shaded.opentelemetry.** { *; }
-dontwarn com.onesignal.shaded.opentelemetry.**

# jctools package-info inside the relocated SDK still names OSGi bundle annotations
# that are not on the Android classpath. Scoped to that annotation package only.
-dontwarn org.osgi.annotation.bundle.**
