package com.onesignal.otel

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import java.time.Instant

/**
 * Helper class for logging to Otel from the Logging class.
 * This abstracts away OpenTelemetry-specific types so the core module
 * doesn't need direct OpenTelemetry dependencies.
 */
object OtelLoggingHelper {
    /**
     * Logs a message to Otel remote telemetry.
     * This method handles all OpenTelemetry-specific types internally.
     *
     * @param telemetry The Otel remote telemetry instance
     * @param level The log level as a string (VERBOSE, DEBUG, INFO, WARN, ERROR, FATAL)
     * @param message The log message
     * @param exceptionType Optional exception type
     * @param exceptionMessage Optional exception message
     * @param exceptionStacktrace Optional exception stacktrace
     */
    suspend fun logToOtel(
        telemetry: IOtelOpenTelemetryRemote,
        level: String,
        message: String,
        exceptionType: String? = null,
        exceptionMessage: String? = null,
        exceptionStacktrace: String? = null,
    ) {
        val severity = when (level.uppercase()) {
            "VERBOSE" -> Severity.TRACE
            "DEBUG" -> Severity.DEBUG
            "INFO" -> Severity.INFO
            "WARN" -> Severity.WARN
            "ERROR" -> Severity.ERROR
            "FATAL" -> Severity.FATAL
            else -> Severity.INFO
        }

        val attributes = Attributes.builder()
            .put("log.message", message)
            .put("log.level", level)
            .apply {
                if (exceptionType != null) {
                    put("exception.type", exceptionType)
                }
                if (exceptionMessage != null) {
                    put("exception.message", exceptionMessage)
                }
                if (exceptionStacktrace != null) {
                    put("exception.stacktrace", exceptionStacktrace)
                }
            }
            .build()

        val logRecordBuilder = telemetry.getLogger()
        logRecordBuilder.setAllAttributes(attributes)
        logRecordBuilder.setSeverity(severity)
        logRecordBuilder.setBody(message)
        logRecordBuilder.setTimestamp(Instant.now())
        logRecordBuilder.emit()
    }
}
