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
 *
 * [key] values are **lowercase** strings as returned from remote config / Turbine `features` arrays.
 */
internal enum class FeatureFlag(
    val key: String,
    val activationMode: FeatureActivationMode
) {
    // Threading mode is selected once per app startup to avoid mixed-mode behavior mid-session.
    // Remote key (lowercase) must match backend / Turbine flag id.
    SDK_BACKGROUND_THREADING(
        "sdk_background_threading",
        FeatureActivationMode.APP_STARTUP
    ),

    /** JWT signing of SDK requests. IMMEDIATE so a kill-switch doesn't need a cold start. */
    IDENTITY_VERIFICATION(
        "identity_verification",
        FeatureActivationMode.IMMEDIATE
    ),
    ;

    fun isEnabledIn(enabledKeys: Set<String>): Boolean {
        return enabledKeys.contains(key)
    }
}
