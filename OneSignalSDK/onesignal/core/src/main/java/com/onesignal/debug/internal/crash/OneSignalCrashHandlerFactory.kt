package com.onesignal.debug.internal.crash

import android.content.Context
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.debug.internal.logging.otel.android.createAndroidOtelPlatformProvider
import com.onesignal.otel.IOtelCrashHandler
import com.onesignal.otel.IOtelLogger
import com.onesignal.otel.OtelFactory

/**
 * Factory for creating Otel-based crash handlers.
 * Callers must verify [OtelSdkSupport.isSupported] before calling [createCrashHandler].
 *
 * Uses minimal dependencies - only Context and logger.
 * Platform provider uses OtelIdResolver internally which reads from SharedPreferences.
 */
internal object OneSignalCrashHandlerFactory {
    /**
     * Creates the Otel crash handler.
     * This should be called as early as possible, before any other initialization.
     *
     * @param context Android context for creating platform provider
     * @param logger Logger instance (can be shared with other components)
     * @throws IllegalArgumentException if called on an unsupported SDK
     */
    fun createCrashHandler(
        context: Context,
        logger: IOtelLogger,
    ): IOtelCrashHandler {
        require(OtelSdkSupport.isSupported) {
            "createCrashHandler called on unsupported SDK (< ${OtelSdkSupport.MIN_SDK_VERSION})"
        }

        Logging.info("OneSignal: Creating Otel crash handler (SDK >= ${OtelSdkSupport.MIN_SDK_VERSION})")
        val platformProvider = createAndroidOtelPlatformProvider(context)
        return OtelFactory.createCrashHandler(platformProvider, logger)
    }
}
