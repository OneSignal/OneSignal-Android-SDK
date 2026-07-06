package com.onesignal.otel.crash

import com.onesignal.otel.IOtelLogger
import com.onesignal.otel.IOtelOpenTelemetryCrash
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import java.time.Instant

internal class OtelCrashReporter(
    private val openTelemetry: IOtelOpenTelemetryCrash,
    private val logger: IOtelLogger,
) : com.onesignal.otel.IOtelCrashReporter {
    companion object {
        private const val OTEL_EXCEPTION_TYPE = "exception.type"
        private const val OTEL_EXCEPTION_MESSAGE = "exception.message"
        private const val OTEL_EXCEPTION_STACKTRACE = "exception.stacktrace"
        private const val OTEL_EXCEPTION_THREAD_NAME = "ossdk.exception.thread.name"

        // Explicit, SDK-owned fatal flag. The backend can segment crash/ANR metrics on this stable
        // attribute rather than inferring intent from severity or exception.type alone, so a
        // non-fatal record can never be double-counted as a crash even if a mapping changes.
        private const val OTEL_FATAL = "ossdk.crash.fatal"
    }

    override suspend fun saveCrash(thread: Thread, throwable: Throwable) =
        save(thread, throwable, severity = Severity.FATAL, fatal = true)

    override suspend fun saveNonFatal(thread: Thread, throwable: Throwable) =
        save(thread, throwable, severity = Severity.WARN, fatal = false)

    private suspend fun save(
        thread: Thread,
        throwable: Throwable,
        severity: Severity,
        fatal: Boolean,
    ) {
        // Capitalized so the fatal path keeps its existing "Crash report ..." log wording.
        val label = if (fatal) "Crash report" else "Non-fatal report"
        try {
            logger.info("OtelCrashReporter: Starting to save ${label.lowercase()} for ${throwable.javaClass.simpleName}")

            val attributes =
                Attributes
                    .builder()
                    .put(OTEL_EXCEPTION_MESSAGE, throwable.message ?: "")
                    .put(OTEL_EXCEPTION_STACKTRACE, throwable.stackTraceToString())
                    .put(OTEL_EXCEPTION_TYPE, throwable.javaClass.name)
                    // This matches the top level thread.name today, but it may not
                    // always if things are refactored to use a different thread.
                    .put(OTEL_EXCEPTION_THREAD_NAME, thread.name)
                    .put(OTEL_FATAL, fatal)
                    .build()

            logger.debug("OtelCrashReporter: Creating log record with attributes...")
            openTelemetry
                .getLogger()
                .setAllAttributes(attributes)
                .setSeverity(severity)
                .setTimestamp(Instant.now())
                .emit()

            logger.debug("OtelCrashReporter: Flushing ${label.lowercase()} to disk...")
            openTelemetry.forceFlush()

            // Note: forceFlush() returns CompletableResultCode which is async
            // We wait for it in the implementation, so if we get here, it succeeded
            logger.info("OtelCrashReporter: ✅ $label saved and flushed successfully to disk")
        } catch (e: RuntimeException) {
            // If we fail to log the crash, at least try to log the failure
            logger.error("OtelCrashReporter: Failed to save crash report: ${e.message} - ${e.javaClass.simpleName}")
            throw e // Re-throw so caller knows it failed
        } catch (e: java.io.IOException) {
            // Handle IO errors specifically
            logger.error("OtelCrashReporter: IO error saving crash report: ${e.message}")
            throw e
        } catch (e: IllegalStateException) {
            // Handle illegal state errors
            logger.error("OtelCrashReporter: Illegal state error saving crash report: ${e.message}")
            throw e
        }
    }
}
