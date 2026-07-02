package com.onesignal.logger

/**
 * Platform-agnostic severity model.
 *
 * Numeric values follow the OpenTelemetry log severity number scheme so that
 * payloads remain wire-compatible with the existing OTLP ingestion endpoint,
 * even though this module no longer depends on the OpenTelemetry SDK.
 *
 * See: https://opentelemetry.io/docs/specs/otel/logs/data-model/#field-severitynumber
 */
enum class LogSeverity(val severityNumber: Int, val severityText: String) {
    TRACE(1, "TRACE"),
    DEBUG(5, "DEBUG"),
    INFO(9, "INFO"),
    WARN(13, "WARN"),
    ERROR(17, "ERROR"),
    FATAL(21, "FATAL"),
    ;

    companion object {
        /**
         * Maps a OneSignal [com.onesignal.debug.LogLevel] name (or any case variant)
         * to a severity. Unknown values fall back to [INFO] to match the previous
         * OtelLoggingHelper behavior.
         */
        fun fromLevelName(level: String): LogSeverity =
            when (level.uppercase()) {
                "VERBOSE" -> TRACE
                "DEBUG" -> DEBUG
                "INFO" -> INFO
                "WARN" -> WARN
                "ERROR" -> ERROR
                "FATAL" -> FATAL
                else -> INFO
            }
    }
}
