package com.onesignal.debug.internal.crash

import android.content.Context
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.debug.internal.logging.otel.android.createAndroidOtelPlatformProvider
import com.onesignal.otel.IOtelCrashHandler
import com.onesignal.otel.IOtelLogger
import com.onesignal.otel.OtelFactory

/**
 * Factory for creating Otel-based crash handlers.
 * Callers must verify [OtelSdkSupport.isSupported] before calling [createCrashHandler].
 *
 * Uses minimal dependencies - Context, logger, and FeatureManager (so per-event OTel attrs
 * can include `ossdk.features_enabled`). Platform provider uses OtelIdResolver internally
 * which reads from SharedPreferences.
 */
internal object OneSignalCrashHandlerFactory {
    /**
     * Creates an Otel crash handler. Must only be called on supported devices
     * (SDK >= [OtelSdkSupport.MIN_SDK_VERSION]).
     *
     * @param context Android context for creating platform provider
     * @param logger Logger instance (can be shared with other components)
     * @param featureManager Feature flag source for `ossdk.features_enabled` per-event attribute
     * @throws IllegalArgumentException if called on an unsupported SDK
     */
    fun createCrashHandler(
        context: Context,
        logger: IOtelLogger,
        featureManager: IFeatureManager,
    ): IOtelCrashHandler {
        require(OtelSdkSupport.isSupported) {
            "createCrashHandler called on unsupported SDK (< ${OtelSdkSupport.MIN_SDK_VERSION})"
        }

        Logging.info("OneSignal: Creating Otel crash handler (SDK >= ${OtelSdkSupport.MIN_SDK_VERSION})")
        val platformProvider = createAndroidOtelPlatformProvider(context, featureManager)
        return OtelFactory.createCrashHandler(platformProvider, logger)
    }
}
