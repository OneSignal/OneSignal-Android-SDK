package com.onesignal.core.internal.features

/**
 * Controls when remote config changes for a feature are applied.
 */
internal enum class FeatureActivationMode {
    /**
     * Apply config changes immediately during the current app run.
     */
    IMMEDIATE,

    /**
     * Latch value at startup; apply remote changes on next app run.
     */
    APP_STARTUP,
}

/**
 * Backend-driven feature switches used by the SDK.
 */
internal enum class FeatureFlag(
    val key: String,
    val activationMode: FeatureActivationMode,
) {
    // Threading mode is selected once per app startup to avoid mixed-mode behavior mid-session.
    BACKGROUND_THREADING("BACKGROUND_THREADING", FeatureActivationMode.APP_STARTUP),
}
