package com.onesignal.logger.crash

import com.onesignal.logger.CrashData
import com.onesignal.logger.ILogCrashReporter
import com.onesignal.logger.ILogTelemetryCrash
import com.onesignal.logger.ILogger
import com.onesignal.logger.LogRecord
import com.onesignal.logger.LogSeverity

/**
 * Persists a captured crash by emitting it to the crash (disk) telemetry sink and
 * forcing a flush so it survives the imminent process death.
 *
 * Mirrors `OtelCrashReporter`, but takes a platform-neutral [CrashData] instead of a
 * JVM `Thread`/`Throwable`.
 */
internal class LogCrashReporter(
    private val crashTelemetry: ILogTelemetryCrash,
    private val logger: ILogger,
) : ILogCrashReporter {
    override suspend fun saveCrash(crash: CrashData) {
        logger.info("LogCrashReporter: saving crash report for ${crash.exceptionType}")

        val body = crash.exceptionMessage.ifBlank { crash.exceptionType }
        val record =
            LogRecord(
                severity = LogSeverity.FATAL,
                body = body,
                attributes =
                mapOf(
                    "exception.message" to crash.exceptionMessage,
                    "exception.stacktrace" to crash.stacktrace,
                    "exception.type" to crash.exceptionType,
                    // Matches the top-level thread.name today, but kept distinct in
                    // case future refactors report from a different thread.
                    "ossdk.exception.thread.name" to crash.threadName,
                ),
            )

        crashTelemetry.emit(record)
        crashTelemetry.forceFlush()
        logger.info("LogCrashReporter: crash report saved and flushed")
    }
}
