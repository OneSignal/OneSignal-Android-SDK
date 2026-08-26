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
 * Android-specific wrapper for the shared crash uploader that implements IStartableService.
 *
 * This is a thin adapter layer that:
 * 1. Takes Android-specific services as dependencies
 * 2. Creates platform-agnostic implementations (ILoggerPlatformProvider, ILogger)
 * 3. Wraps the platform-agnostic LogCrashUploader for Android service architecture
 *
 * The uploader itself is fully platform-agnostic and can be used directly in KMP projects
 * by providing platform-specific implementations of:
 * - ILoggerPlatformProvider (inject all platform values)
 * - ILogger (platform logging interface)
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
                // Shared LogCrashUploader.start() is suspend and finishes the owned-record
                // upload pass plus the finally-purge before returning, so the after-cleanup
                // inventory below is not racing a background purge.
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
     * Resolves the crash directory the logger module reads and writes. Uses the pure path
     * helper rather than a provider: building one costs a `PackageManager` round-trip and an
     * ID resolver, and re-emits the provider's "Crash logs stored at" line, all to read a
     * value derived from the context alone.
     */
    private fun crashStoragePath(): String = getCrashStoragePath(applicationService.appContext)

    /**
     * Logs a snapshot of the crash dir (counts of owned `.otlp` vs foreign/legacy
     * entries, plus a bounded per-file sample) so leftover formats are visible and
     * cleanup is verifiable from logs alone.
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
                        lastModifiedMs = file.lastModified(),
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
