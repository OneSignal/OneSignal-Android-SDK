package com.onesignal.debug.internal.logging.otel

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.onesignal.debug.internal.crash.IOneSignalCrashReporter
import io.opentelemetry.api.common.Attributes
import java.io.PrintWriter
import java.io.StringWriter

@RequiresApi(Build.VERSION_CODES.O)
internal class OneSignalCrashReporterOtel(
    val _openTelemetry: IOneSignalOpenTelemetryCrash
) : IOneSignalCrashReporter {
    companion object {
        private const val EXCEPTION_TYPE = "exception.type"
        private const val EXCEPTION_MESSAGE = "exception.message"
        private const val EXCEPTION_STACKTRACE = "exception.stacktrace"
    }

    override suspend fun sendCrash(therad: Thread, throwable: Throwable) {
        Log.e("OSCrashHandling", "sendCrash TOP")
        val attributesBuilder =
            Attributes
                .builder()
                .put(EXCEPTION_STACKTRACE, throwable.stackTraceToString())
                .put(EXCEPTION_TYPE, throwable.javaClass.name)
                .build()
        // TODO:1: Remaining attributes
        // TODO:1.1: process name:
//        final String processName = ActivityThread.currentProcessName();
//        if (processName != null) {
//            message.append("Process: ").append(processName).append(", ");
//        }

        _openTelemetry.logger
            .logRecordBuilder()
            .setAllAttributes(attributesBuilder)
            .emit()

        _openTelemetry.forceFlush()
        Log.e("OSCrashHandling", "sendCrash BOTTOM")
    }

    private fun stackTraceToString(throwable: Throwable): String {
        val stringWriter = StringWriter(256)
        val printWriter = PrintWriter(stringWriter)

        throwable.printStackTrace(printWriter)
        printWriter.flush()

        return stringWriter.toString()
    }
}
