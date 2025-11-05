package com.onesignal.debug.internal.logging.otel.crash

import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.debug.internal.logging.otel.crash.IOneSignalCrashConfigProvider
import com.onesignal.debug.internal.logging.otel.IOneSignalOpenTelemetryRemote
import com.onesignal.debug.internal.logging.otel.config.OtelConfigCrashFile
import io.opentelemetry.sdk.logs.data.LogRecordData
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/**
 * Purpose: This reads a local crash report files created by OneSignal's
 *   crash handler and sends them to OneSignal on the app's next start.
 */
internal class OneSignalCrashUploader(
    private val _openTelemetryRemote: IOneSignalOpenTelemetryRemote,
    private val _crashPathProvider: IOneSignalCrashConfigProvider,
) : IStartableService {
    companion object {
        const val SEND_TIMEOUT_SECONDS = 30L
    }

    private fun getReports() =
        OtelConfigCrashFile.SdkLoggerProviderConfig
            .getFileLogRecordStorage(
                _crashPathProvider.path,
                _crashPathProvider.minFileAgeForReadMillis
            ).iterator()

    override fun start() {
        runBlocking { internalStart() }
    }

    /**
     * NOTE: sendCrashReports is called twice for the these reasons:
     *   1. We want to send crash reports as soon as possible.
     *     - App may crash quickly after starting a 2nd time.
     *   2. Reports could be delayed until the 2nd start after a crash
     *     - Otel doesn't let you read a file it could be writing so we must
     *       wait a minium amount of time after a crash to ensure we get the
     *       report from the last crash.
     */
    suspend fun internalStart() {
        sendCrashReports(getReports())
        delay(_crashPathProvider.minFileAgeForReadMillis)
        sendCrashReports(getReports())
    }

    private fun sendCrashReports(reports: Iterator<Collection<LogRecordData>>) {
        val networkExporter = _openTelemetryRemote.logExporter
        var failed = false
        // NOTE: next() will delete the previous report, so we only want to send
        // another one if there isn't an issue making network calls.
        while (reports.hasNext() && !failed) {
            val future = networkExporter.export(reports.next())
            val result = future.join(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            failed = !result.isSuccess
        }
    }
}
