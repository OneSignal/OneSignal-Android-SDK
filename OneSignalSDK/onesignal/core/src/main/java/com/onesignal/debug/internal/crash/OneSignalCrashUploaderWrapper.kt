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
import com.onesignal.logger.ILogTelemetryRemote
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
    @Suppress("TooGenericExceptionCaught")
    override fun start() {
        if (!ObservabilitySdkSupport.isSupported) return
        OneSignalDispatchers.launchOnIO {
            try {
                logCrashDirInventory("before-upload")
                // The pass completes the upload and the finally-purge before returning, so the
                // after-cleanup inventory below is not racing a background purge.
                runUploadPass()
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
     * This remote belongs to the pass alone: the uploader posts pre-encoded records straight out
     * and never enqueues, so its batch loop is dead weight that would tick until process death.
     */
    private suspend fun runUploadPass() {
        val platformProvider = createAndroidLoggerPlatformProvider(applicationService.appContext) { featureManager }
        val logger = AndroidLogger()
        val httpSender = OneSignalLogHttpSender(logger) { platformProvider.isExporterLoggingEnabled }
        val remote = LoggerFactory.createRemoteTelemetry(platformProvider, httpSender)
        try {
            val fileStore = FileLogStore(platformProvider.crashStoragePath)
            LoggerFactory.createCrashUploader(platformProvider, remote, fileStore, logger).start()
        } finally {
            shutdownRemote(remote)
        }
    }

    /** Runs from a finally, so a throwing teardown must not replace the failure that got us here. */
    @Suppress("TooGenericExceptionCaught")
    private fun shutdownRemote(remote: ILogTelemetryRemote) {
        try {
            remote.shutdown()
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Crash uploader telemetry failed to shut down: ${t.message}", t)
        }
    }

    /** Uses the pure path helper, not a provider: that costs a `PackageManager` round-trip. */
    private fun crashStoragePath(): String = getCrashStoragePath(applicationService.appContext)

    /**
     * Snapshot of the crash dir, so cleanup is verifiable from logs alone. Report an unreadable
     * `lastModified()` as unknown, as [FileLogStore] does; a fabricated age misdirects.
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
