package com.onesignal.otel.crash

import com.onesignal.otel.IOtelLogger
import com.onesignal.otel.IOtelOpenTelemetryRemote
import com.onesignal.otel.IOtelPlatformProvider
import com.onesignal.otel.config.OtelConfigCrashFile
import io.opentelemetry.sdk.logs.data.LogRecordData
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Purpose: This reads a local crash report files created by OneSignal's
 *   crash handler and sends them to OneSignal on the app's next start.
 *
 * This is fully platform-agnostic and can be used in KMP projects.
 * All platform-specific values are injected through IOtelPlatformProvider.
 *
 * Dependencies (all platform-agnostic):
 * - IOtelOpenTelemetryRemote: For network export (created via OtelFactory)
 * - IOtelPlatformProvider: Injects all platform values (Android/iOS)
 * - IOtelLogger: Platform logging interface (Android/iOS)
 *
 * Usage:
 * ```kotlin
 * val uploader = OtelFactory.createCrashUploader(platformProvider, logger)
 * coroutineScope.launch {
 *     uploader.start()
 * }
 * ```
 */
class OtelCrashUploader(
    private val openTelemetryRemote: IOtelOpenTelemetryRemote,
    private val platformProvider: IOtelPlatformProvider,
    private val logger: IOtelLogger,
) {
    companion object {
        const val SEND_TIMEOUT_SECONDS = 30L
    }

    private fun getReports() =
        OtelConfigCrashFile.SdkLoggerProviderConfig
            .getFileLogRecordStorage(
                platformProvider.crashStoragePath,
                platformProvider.minFileAgeForReadMillis
            ).iterator()

    suspend fun start() {
        if (!platformProvider.remoteLoggingEnabled) {
            logger.info("OtelCrashUploader: remote logging disabled")
            return
        }

        logger.info("OtelCrashUploader: starting")
        internalStart()
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
        delay(platformProvider.minFileAgeForReadMillis)
        sendCrashReports(getReports())
    }

    private fun sendCrashReports(reports: Iterator<Collection<LogRecordData>>) {
        val networkExporter = openTelemetryRemote.logExporter
        var failed = false
        // NOTE: next() will delete the previous report, so we only want to send
        // another one if there isn't an issue making network calls.
        while (reports.hasNext() && !failed) {
            val future = networkExporter.export(reports.next())
            logger.debug("Sending OneSignal crash report")
            val result = future.join(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            failed = !result.isSuccess
            logger.debug("Done OneSignal crash report, failed: $failed")
        }
    }
}
