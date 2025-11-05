package com.onesignal.debug.internal.logging.otel.crash

import com.onesignal.debug.internal.crash.IOneSignalCrashReporter
import com.onesignal.debug.internal.logging.otel.IOneSignalOpenTelemetryCrash
import com.onesignal.debug.internal.logging.otel.attributes.OS_OTEL_NAMESPACE
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity

internal class OneSignalCrashReporterOtel(
    val _openTelemetry: IOneSignalOpenTelemetryCrash
) : IOneSignalCrashReporter {
    companion object {
        private const val OTEL_EXCEPTION_TYPE = "exception.type"
        private const val OTEL_EXCEPTION_MESSAGE = "exception.message"
        private const val OTEL_EXCEPTION_STACKTRACE = "exception.stacktrace"

    }

    override suspend fun sendCrash(thread: Thread, throwable: Throwable) {
        val attributesBuilder =
            Attributes
                .builder()
                .put(OTEL_EXCEPTION_MESSAGE, throwable.message)
                .put(OTEL_EXCEPTION_STACKTRACE, throwable.stackTraceToString())
                .put(OTEL_EXCEPTION_TYPE, throwable.javaClass.name)
                // This matches the top level thread.name today, but it may not
                // always if things are refactored to use a different thread.
                .put("$OS_OTEL_NAMESPACE.exception.thread.name", thread.name)
                .build()

        _openTelemetry.getLogger()
            .setAllAttributes(attributesBuilder)
            .setSeverity(Severity.FATAL)
            .emit()

        _openTelemetry.forceFlush()
    }
}
