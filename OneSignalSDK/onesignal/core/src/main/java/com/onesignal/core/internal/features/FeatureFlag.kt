package com.onesignal.core.internal.features

internal enum class FeatureActivationMode {
    RUNTIME,
    NEXT_RUN,
}

internal enum class FeatureFlag(
    val key: String,
    val activationMode: FeatureActivationMode,
) {
    BACKGROUND_THREADING("BACKGROUND_THREADING", FeatureActivationMode.NEXT_RUN),
}
