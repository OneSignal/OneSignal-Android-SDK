package com.onesignal.debug.internal.crash

import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.debug.internal.logging.logger.android.AndroidLogger
import com.onesignal.debug.internal.logging.logger.android.FileLogStore
import com.onesignal.debug.internal.logging.logger.android.OneSignalLogHttpSender
import com.onesignal.debug.internal.logging.logger.android.createAndroidLoggerPlatformProvider
import com.onesignal.debug.internal.logging.logger.android.getCrashStoragePath
import com.onesignal.logger.LoggerFactory
import com.onesignal.logger.crash.CrashDirEntry
import com.onesignal.logger.crash.CrashRetention
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Adapts the shared, platform-agnostic `LogCrashUploader` to [IStartableService], supplying it
 * with Android implementations of `ILoggerPlatformProvider` and `ILogger`.
 */
internal class OneSignalCrashUploaderWrapper(
    private val applicationService: IApplicationService,
    private val featureManager: IFeatureManager,
) : IStartableService {
    private val uploader by lazy {
        val platformProvider = createAndroidLoggerPlatformProvider(applicationService.appContext) { featureManager }
        val logger = AndroidLogger()
        val httpSender = OneSignalLogHttpSender(logger) { platformProvider.isExporterLoggingEnabled }
        val remote = LoggerFactory.createRemoteTelemetry(platformProvider, httpSender)
        val fileStore = FileLogStore(platformProvider.crashStoragePath)
        LoggerFactory.createCrashUploader(platformProvider, remote, fileStore, logger)
    }

    @Suppress("TooGenericExceptionCaught")
    override fun start() {
        if (!ObservabilitySdkSupport.isSupported) return
        OneSignalDispatchers.launchOnIO {
            try {
                logCrashDirInventory("before-upload")
                // start() completes the upload pass and the finally-purge before returning, so
                // the after-cleanup inventory below is not racing a background purge.
                uploader.start()
                logCrashDirInventory("after-cleanup")
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Logging.warn(
                    "OneSignal: Crash uploader failed to start: ${t.message}",
                    t,
                )
            }
        }
    }

    /**
     * Resolves the crash directory via the pure path helper rather than a provider: the value is
     * derived from the context alone, and building a provider costs a `PackageManager` round-trip
     * and re-emits its "Crash logs stored at" line.
     */
    private fun crashStoragePath(): String = getCrashStoragePath(applicationService.appContext)

    /**
     * Logs a snapshot of the crash dir (counts of owned `.otlp` vs foreign/legacy
     * entries, plus a bounded per-file sample) so leftover formats are visible and
     * cleanup is verifiable from logs alone.
     *
     * Reports an unreadable `lastModified()` as unknown for the same reason [FileLogStore] does,
     * so the sampled age is either the one retention actually used or `unknown` — an inventory
     * printing a fabricated age would misdirect exactly the investigation it exists to support.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun logCrashDirInventory(label: String) {
        try {
            val path = crashStoragePath()
            val now = System.currentTimeMillis()
            val entries =
                File(path).listFiles()?.filter { it.isFile }?.map { file ->
                    CrashDirEntry(
                        name = file.name,
                        lastModifiedMs = file.lastModified().takeIf { it > 0 },
                        lengthBytes = file.length(),
                    )
                }.orEmpty()
            Logging.info(
                CrashRetention.formatInventory(
                    label = label,
                    path = path,
                    entries = entries,
                    nowMs = now,
                    maxSample = MAX_INVENTORY_SAMPLE,
                    policy = CrashRetention.defaultPolicy,
                ),
            )
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Crash storage inventory failed: ${t.message}", t)
        }
    }

    private companion object {
        const val MAX_INVENTORY_SAMPLE = 20
    }
}
