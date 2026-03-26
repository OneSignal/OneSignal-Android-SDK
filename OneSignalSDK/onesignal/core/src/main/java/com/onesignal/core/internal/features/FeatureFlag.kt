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
    val activationMode: FeatureActivationMode
) {
    // Threading mode is selected once per app startup to avoid mixed-mode behavior mid-session.
    //
    // Naming convention:
    //   SDK_<MMmmpp>_<FEATURE_DESCRIPTION>
    //
    // Example:
    //   SDK_050800_BACKGROUND_THREADING
    //
    SDK_050800_BACKGROUND_THREADING(
        "SDK_050800_BACKGROUND_THREADING",
        FeatureActivationMode.APP_STARTUP
    ),
    ;

    fun isEnabledIn(enabledKeys: Set<String>): Boolean {
        return enabledKeys.contains(key)
    }
}
