package com.onesignal.debug.internal.crash

import android.content.Context
import android.os.Build
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.debug.internal.logging.otel.android.createAndroidOtelPlatformProvider
import com.onesignal.otel.IOtelCrashHandler
import com.onesignal.otel.IOtelLogger
import com.onesignal.otel.OtelFactory

/**
 * Factory for creating crash handlers with SDK version checks.
 * For SDK < 26, returns a no-op implementation.
 * For SDK >= 26, returns the Otel-based crash handler.
 *
 * Uses minimal dependencies - only Context and logger.
 * Platform provider uses OtelIdResolver internally which reads from SharedPreferences.
 */
internal object OneSignalCrashHandlerFactory {
    /**
     * Creates a crash handler appropriate for the current SDK version.
     * This should be called as early as possible, before any other initialization.
     *
     * @param context Android context for creating platform provider
     * @param logger Logger instance (can be shared with other components)
     */
    fun createCrashHandler(
        context: Context,
        logger: IOtelLogger,
    ): IOtelCrashHandler {
        // Otel requires SDK 26+, use no-op for older versions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Logging.info("OneSignal: Creating no-op crash handler (SDK ${Build.VERSION.SDK_INT} < 26)")
            return NoOpCrashHandler()
        }

        Logging.info("OneSignal: Creating Otel crash handler (SDK ${Build.VERSION.SDK_INT} >= 26)")
        // Create platform provider - uses OtelIdResolver internally
        val platformProvider = createAndroidOtelPlatformProvider(context)
        return OtelFactory.createCrashHandler(platformProvider, logger)
    }
}

/**
 * No-op crash handler for SDK < 26.
 */
private class NoOpCrashHandler : IOtelCrashHandler {
    override fun initialize() {
        Logging.info("OneSignal: No-op crash handler initialized (SDK < 26, Otel not supported)")
    }
}
