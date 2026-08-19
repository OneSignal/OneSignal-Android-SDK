package com.onesignal.otel.crash

import com.onesignal.otel.IOtelLogger
import com.onesignal.otel.IOtelOpenTelemetryRemote
import com.onesignal.otel.IOtelPlatformProvider
import com.onesignal.otel.IOtelSdkRemoteTelemetry
import com.onesignal.otel.config.OtelConfigCrashFile
import io.opentelemetry.sdk.logs.data.LogRecordData
import kotlinx.coroutines.delay
import java.io.File
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
        private const val MAX_PREVIEW_RECORDS = 3
        private const val MAX_BODY_PREVIEW_CHARS = 120
        private const val MAX_PREVIEW_ATTR_KEYS = 8
    }

    private fun getReports() =
        OtelConfigCrashFile.SdkLoggerProviderConfig
            .getFileLogRecordStorage(
                platformProvider.crashStoragePath,
                platformProvider.minFileAgeForReadMillis
            ).iterator()

    /**
     * Starts the crash uploader process.
     * This will periodically check for crash reports on disk and upload them to OneSignal.
     * If remote logging is disabled (NONE level), this function returns immediately without doing anything.
     */
    suspend fun start() {
        val remoteLogLevel = platformProvider.remoteLogLevel
        if (remoteLogLevel == null || remoteLogLevel == "NONE") {
            logger.info("OtelCrashUploader: remote logging disabled (level: $remoteLogLevel)")
            return
        }

        logger.info(
            "OtelCrashUploader: starting path=${platformProvider.crashStoragePath} " +
                "minFileAgeMs=${platformProvider.minFileAgeForReadMillis} level=$remoteLogLevel",
        )
        logDiskFiles("before-read")
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
        logDiskFiles("after-upload-passes")
    }

    internal fun sendCrashReports(reports: Iterator<Collection<LogRecordData>>) {
        val networkExporter = (openTelemetryRemote as IOtelSdkRemoteTelemetry).logExporter
        var failed = false
        var sentBatches = 0
        // NOTE: next() will delete the previous report, so we only want to send
        // another one if there isn't an issue making network calls.
        while (reports.hasNext() && !failed) {
            val batch = reports.next()
            logger.info(
                "OtelCrashUploader: posting batch records=${batch.size} preview=[${summarizeRecords(batch)}]",
            )
            val future = networkExporter.export(batch)
            val result = future.join(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            failed = !result.isSuccess
            if (!failed) sentBatches++
            logger.info("OtelCrashUploader: batch done failed=$failed")
        }
        logger.info("OtelCrashUploader: pass complete sentBatches=$sentBatches stoppedOnFailure=$failed")
    }

    internal fun logDiskFiles(label: String) {
        val dir = File(platformProvider.crashStoragePath)
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        if (files.isEmpty()) {
            logger.info("OtelCrashUploader: disk $label — no files in ${dir.path}")
            return
        }
        val summary =
            files.joinToString(separator = "; ") { file ->
                "name=${file.name} bytes=${file.length()}"
            }
        logger.info("OtelCrashUploader: disk $label count=${files.size} [$summary]")
    }

    internal fun summarizeRecords(batch: Collection<LogRecordData>): String =
        batch.take(MAX_PREVIEW_RECORDS).joinToString(separator = " | ") { record ->
            val body = runCatching { record.body.asString() }.getOrNull()?.take(MAX_BODY_PREVIEW_CHARS)
            val attrs = record.attributes.asMap().keys.take(MAX_PREVIEW_ATTR_KEYS).joinToString(",")
            "severity=${record.severityText} body=$body attrs=[$attrs]"
        }
}
