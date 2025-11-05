package com.onesignal.debug.internal.logging.otel.crash

import android.util.Log
import com.onesignal.debug.internal.crash.IOneSignalCrashReporter
import com.onesignal.debug.internal.logging.otel.IOneSignalOpenTelemetryCrash
import io.opentelemetry.api.common.Attributes

internal class OneSignalCrashReporterOtel(
    val _openTelemetry: IOneSignalOpenTelemetryCrash
) : IOneSignalCrashReporter {
    companion object {
        private const val EXCEPTION_TYPE = "exception.type"
        private const val EXCEPTION_MESSAGE = "exception.message"
        private const val EXCEPTION_STACKTRACE = "exception.stacktrace"
    }

    override suspend fun sendCrash(thread: Thread, throwable: Throwable) {
        Log.e("OSCrashHandling", "sendCrash TOP")
        val attributesBuilder =
            Attributes
                .builder()
                .put(EXCEPTION_MESSAGE, throwable.message)
                .put(EXCEPTION_STACKTRACE, throwable.stackTraceToString())
                .put(EXCEPTION_TYPE, throwable.javaClass.name)
                .build()
        // TODO:1: Remaining attributes
        // TODO:1.1: process name:
//        final String processName = ActivityThread.currentProcessName();
//        if (processName != null) {
//            message.append("Process: ").append(processName).append(", ");
//        }

        _openTelemetry.getLogger()
            .logRecordBuilder()
            .setAllAttributes(attributesBuilder)
            .emit()

        _openTelemetry.forceFlush()
        Log.e("OSCrashHandling", "sendCrash BOTTOM")
    }
}
