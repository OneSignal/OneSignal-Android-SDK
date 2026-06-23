package com.onesignal.core.internal.features

/**
 * Controls when remote config changes for a feature are applied.
 */
enum class FeatureActivationMode {
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
enum class FeatureFlag(
    val key: String,
    val activationMode: FeatureActivationMode
) {
    /** JWT signing of SDK requests. IMMEDIATE so a kill-switch doesn't need a cold start. */
    SDK_IDENTITY_VERIFICATION(
        "sdk_identity_verification",
        FeatureActivationMode.IMMEDIATE
    ),
    ;

    fun isEnabledIn(enabledKeys: Set<String>): Boolean {
        return enabledKeys.contains(key)
    }
}
