package com.onesignal.logger

/**
 * Helper for turning a log call into a [LogRecord] and emitting it. Mirrors
 * `OtelLoggingHelper`, giving the platform `Logging` integration a single, stable
 * entry point that hides the record-construction details.
 */
object LogLoggingHelper {
    suspend fun log(
        telemetry: ILogTelemetry,
        level: String,
        message: String,
        exceptionType: String? = null,
        exceptionMessage: String? = null,
        exceptionStacktrace: String? = null,
    ) {
        val attributes =
            buildMap {
                put("log.message", message)
                put("log.level", level)
                if (exceptionType != null) put("exception.type", exceptionType)
                if (exceptionMessage != null) put("exception.message", exceptionMessage)
                if (exceptionStacktrace != null) put("exception.stacktrace", exceptionStacktrace)
            }

        telemetry.emit(
            LogRecord(
                severity = LogSeverity.fromLevelName(level),
                body = message,
                attributes = attributes,
            ),
        )
    }
}
