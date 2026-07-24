package com.onesignal.debug.internal.crash

import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.debug.internal.logging.logger.LoggerModuleSwitch
import com.onesignal.debug.internal.logging.logger.android.AndroidLogger
import com.onesignal.debug.internal.logging.logger.android.FileLogStore
import com.onesignal.debug.internal.logging.logger.android.OneSignalLogHttpSender
import com.onesignal.debug.internal.logging.logger.android.createAndroidLoggerPlatformProvider
import com.onesignal.debug.internal.logging.otel.android.AndroidOtelLogger
import com.onesignal.debug.internal.logging.otel.android.createAndroidOtelPlatformProvider
import com.onesignal.logger.LoggerFactory
import com.onesignal.otel.OtelFactory
import com.onesignal.otel.crash.OtelCrashUploader
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Android-specific wrapper for OtelCrashUploader that implements IStartableService.
 *
 * This is a thin adapter layer that:
 * 1. Takes Android-specific services as dependencies
 * 2. Creates platform-agnostic implementations (IOtelPlatformProvider, IOtelLogger)
 * 3. Wraps the platform-agnostic OtelCrashUploader for Android service architecture
 *
 * The OtelCrashUploader itself is fully platform-agnostic and can be used directly
 * in KMP projects by providing platform-specific implementations of:
 * - IOtelPlatformProvider (inject all platform values)
 * - IOtelLogger (platform logging interface)
 *
 * Example KMP usage:
 * ```kotlin
 * val platformProvider = MyPlatformProvider(...) // iOS/Android specific
 * val logger = MyPlatformLogger() // iOS/Android specific
 * val uploader = OtelFactory.createCrashUploader(platformProvider, logger)
 * // Use uploader.start() in a coroutine
 * ```
 */
internal class OneSignalCrashUploaderWrapper(
    private val applicationService: IApplicationService,
    private val featureManager: IFeatureManager,
) : IStartableService {
    private val otelUploader: OtelCrashUploader by lazy {
        // Create Android-specific platform provider (injects Android values + a FeatureManager
        // supplier that resolves to the constructor-injected manager on each access).
        val platformProvider = createAndroidOtelPlatformProvider(
            applicationService.appContext,
        ) { featureManager }
        // Create Android-specific logger (delegates to Android Logging)
        val logger = AndroidOtelLogger()
        // Create platform-agnostic uploader using factory
        OtelFactory.createCrashUploader(platformProvider, logger)
    }

    private val loggerUploader by lazy {
        val platformProvider = createAndroidLoggerPlatformProvider(applicationService.appContext) { featureManager }
        val logger = AndroidLogger()
        val httpSender = OneSignalLogHttpSender(logger) { platformProvider.isExporterLoggingEnabled }
        val remote = LoggerFactory.createRemoteTelemetry(platformProvider, httpSender)
        val fileStore = FileLogStore(platformProvider.crashStoragePath)
        LoggerFactory.createCrashUploader(platformProvider, remote, fileStore, logger)
    }

    @Suppress("TooGenericExceptionCaught")
    override fun start() {
        if (!OtelSdkSupport.isSupported) return
        OneSignalDispatchers.launchOnIO {
            try {
                val useLogger = LoggerModuleSwitch.useLoggerModule(applicationService.appContext)
                val module = if (useLogger) "logger" else "otel"
                Logging.info("OneSignal: Crash uploader selecting module=$module (SDK_CUSTOM_LOGGING=$useLogger)")
                logCrashDirInventory("before-upload")
                if (useLogger) {
                    // Shared LogCrashUploader owns the legacy-file purge via
                    // ILogFileStore.deleteUnrecognizedEntries() (FileLogStore).
                    loggerUploader.start()
                    logCrashDirInventory("after-cleanup")
                } else {
                    otelUploader.start()
                }
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

    /** Resolves the shared crash directory both modules write to. */
    private fun crashStoragePath(): String =
        createAndroidOtelPlatformProvider(applicationService.appContext) { featureManager }
            .crashStoragePath

    /**
     * Logs a snapshot of the shared crash dir (counts of owned `.otlp` vs foreign/legacy
     * entries, plus a bounded per-file sample) so leftover formats are visible and
     * cleanup is verifiable from logs alone.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun logCrashDirInventory(label: String) {
        try {
            val path = crashStoragePath()
            val dir = File(path)
            val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
            if (files.isEmpty()) {
                Logging.info("OneSignal: Crash storage inventory [$label] ($path): empty")
                return
            }
            val otlp = files.count { it.name.endsWith(".otlp") }
            val legacy = files.size - otlp
            val now = System.currentTimeMillis()
            val sample = files.take(MAX_INVENTORY_SAMPLE)
            val summary =
                sample.joinToString(separator = "; ") { file ->
                    "name=${file.name} bytes=${file.length()} ageMs=${now - file.lastModified()}"
                }
            val truncated =
                if (files.size > MAX_INVENTORY_SAMPLE) {
                    " …(+${files.size - MAX_INVENTORY_SAMPLE} more)"
                } else {
                    ""
                }
            Logging.info(
                "OneSignal: Crash storage inventory [$label] ($path): " +
                    "total=${files.size} otlp=$otlp legacy=$legacy [$summary]$truncated",
            )
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Crash storage inventory failed: ${t.message}", t)
        }
    }

    private companion object {
        const val MAX_INVENTORY_SAMPLE = 20
    }
}
