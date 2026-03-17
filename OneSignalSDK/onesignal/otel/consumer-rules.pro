# OpenTelemetry OTLP exporter references Jackson core classes that are optional on Android.
# Suppress R8 missing-class errors when apps don't include jackson-core.
-dontwarn com.fasterxml.jackson.core.**
