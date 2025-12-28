package com.onesignal.debug.internal.crash

import android.os.Build
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IInstallIdService
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.internal.logging.otel.android.AndroidOtelLogger
import com.onesignal.debug.internal.logging.otel.android.AndroidOtelPlatformProvider
import com.onesignal.otel.IOtelCrashHandler
import com.onesignal.otel.OtelFactory
import com.onesignal.user.internal.identity.IdentityModelStore

/**
 * Factory for creating crash handlers with SDK version checks.
 * For SDK < 26, returns a no-op implementation.
 * For SDK >= 26, returns the Otel-based crash handler.
 */
internal object OneSignalCrashHandlerFactory {
    /**
     * Creates a crash handler appropriate for the current SDK version.
     * This should be called as early as possible, before any other initialization.
     * All dependencies must be pre-populated.
     */
    fun createCrashHandler(
        applicationService: IApplicationService,
        installIdService: IInstallIdService,
        configModelStore: ConfigModelStore,
        identityModelStore: IdentityModelStore,
        time: ITime,
    ): IOtelCrashHandler {
        // Otel requires SDK 26+, use no-op for older versions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            com.onesignal.debug.internal.logging.Logging.info("OneSignal: Creating no-op crash handler (SDK ${Build.VERSION.SDK_INT} < 26)")
            return NoOpCrashHandler()
        }

        com.onesignal.debug.internal.logging.Logging.info("OneSignal: Creating Otel crash handler (SDK ${Build.VERSION.SDK_INT} >= 26)")
        val platformProvider = AndroidOtelPlatformProvider(
            applicationService,
            installIdService,
            configModelStore,
            identityModelStore,
            time
        )
        val logger = AndroidOtelLogger()

        return OtelFactory.createCrashHandler(platformProvider, logger)
    }
}

/**
 * No-op crash handler for SDK < 26.
 */
private class NoOpCrashHandler : IOtelCrashHandler {
    override fun initialize() {
        com.onesignal.debug.internal.logging.Logging.info("OneSignal: No-op crash handler initialized (SDK < 26, Otel not supported)")
    }
}
