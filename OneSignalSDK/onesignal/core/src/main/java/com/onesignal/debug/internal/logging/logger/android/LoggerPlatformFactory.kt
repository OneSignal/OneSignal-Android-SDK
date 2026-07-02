package com.onesignal.debug.internal.logging.logger.android

import android.content.Context
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.debug.internal.logging.otel.android.createAndroidOtelPlatformProvider
import com.onesignal.logger.ILoggerPlatformProvider

/**
 * Builds an [ILoggerPlatformProvider] for Android by adapting the existing Android
 * platform provider. Centralizes the wiring so the lifecycle manager and crash
 * uploader wrapper construct it identically.
 */
internal fun createAndroidLoggerPlatformProvider(
    context: Context,
    featureManagerProvider: () -> IFeatureManager,
): ILoggerPlatformProvider =
    LoggerPlatformProviderAdapter(
        createAndroidOtelPlatformProvider(context, featureManagerProvider),
    )
