package com.onesignal.debug.internal.crash

import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.core.internal.startup.IStartableService
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
        val remote = LoggerFactory.createRemoteTelemetry(platformProvider, OneSignalLogHttpSender())
        val fileStore = FileLogStore(platformProvider.crashStoragePath)
        LoggerFactory.createCrashUploader(platformProvider, remote, fileStore, AndroidLogger())
    }

    @Suppress("TooGenericExceptionCaught")
    override fun start() {
        if (!OtelSdkSupport.isSupported) return
        OneSignalDispatchers.launchOnIO {
            try {
                if (LoggerModuleSwitch.USE_LOGGER_MODULE) {
                    loggerUploader.start()
                } else {
                    otelUploader.start()
                }
            } catch (t: Throwable) {
                com.onesignal.debug.internal.logging.Logging.warn(
                    "OneSignal: Crash uploader failed to start: ${t.message}",
                    t,
                )
            }
        }
    }
}
