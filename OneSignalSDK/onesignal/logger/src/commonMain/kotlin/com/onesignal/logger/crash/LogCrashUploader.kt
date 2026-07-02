package com.onesignal.logger.crash

import com.onesignal.logger.ILogFileStore
import com.onesignal.logger.ILogTelemetryRemote
import com.onesignal.logger.ILogger
import com.onesignal.logger.ILoggerPlatformProvider
import kotlinx.coroutines.delay

/**
 * Reads locally-buffered crash reports and ships them to OneSignal on the next app
 * start. Mirrors `OtelCrashUploader`, but reads our own simple disk format instead of
 * OpenTelemetry's disk-buffering library.
 *
 * Usage:
 * ```kotlin
 * val uploader = LoggerFactory.createCrashUploader(provider, remote, fileStore, logger)
 * scope.launch { uploader.start() }
 * ```
 */
class LogCrashUploader internal constructor(
    private val platformProvider: ILoggerPlatformProvider,
    private val remote: ILogTelemetryRemote,
    private val fileStore: ILogFileStore,
    private val logger: ILogger,
) {
    /**
     * Starts the uploader. No-op when remote logging is disabled (NONE / null level).
     */
    suspend fun start() {
        val remoteLogLevel = platformProvider.remoteLogLevel
        if (remoteLogLevel == null || remoteLogLevel == "NONE") {
            logger.info("LogCrashUploader: remote logging disabled (level: $remoteLogLevel)")
            return
        }
        logger.info("LogCrashUploader: starting")
        internalStart()
    }

    /**
     * Sends reports twice for the same reasons as the old uploader:
     *  1. Send crash reports as soon as possible (app may crash again quickly).
     *  2. A report from the previous crash may only become readable after
     *     [ILoggerPlatformProvider.minFileAgeForReadMillis] has elapsed (so we never
     *     read a file the crashing process may still have been writing).
     */
    internal suspend fun internalStart() {
        sendReports()
        delay(platformProvider.minFileAgeForReadMillis)
        sendReports()
    }

    private suspend fun sendReports() {
        val reports = fileStore.listReadable(platformProvider.minFileAgeForReadMillis)
        for (report in reports) {
            logger.debug("LogCrashUploader: sending crash report ${report.id}")
            val success = remote.exportEncoded(report.bytes)
            logger.debug("LogCrashUploader: done crash report ${report.id}, success: $success")
            if (success) {
                // Only delete on success so a failed upload is retried next launch.
                fileStore.delete(report.id)
            } else {
                // Stop on first failure to avoid hammering a failing network.
                break
            }
        }
    }
}
