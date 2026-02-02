package com.onesignal.debug.internal.crash

import android.os.Build
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.debug.internal.logging.otel.android.AndroidOtelLogger
import com.onesignal.debug.internal.logging.otel.android.createAndroidOtelPlatformProvider
import com.onesignal.otel.OtelFactory
import com.onesignal.otel.crash.OtelCrashUploader
import kotlinx.coroutines.runBlocking

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
) : IStartableService {
    private val uploader: OtelCrashUploader by lazy {
        // Create Android-specific platform provider (injects Android values)
        val platformProvider = createAndroidOtelPlatformProvider(
            applicationService.appContext
        )
        // Create Android-specific logger (delegates to Android Logging)
        val logger = AndroidOtelLogger()
        // Create platform-agnostic uploader using factory
        OtelFactory.createCrashUploader(platformProvider, logger)
    }

    override fun start() {
        // Otel library requires Android Oreo (8) or newer
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        runBlocking {
            uploader.start()
        }
    }
}
