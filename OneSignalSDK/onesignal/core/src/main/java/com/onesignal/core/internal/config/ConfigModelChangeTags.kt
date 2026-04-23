package com.onesignal.core.internal.config

/**
 * [ModelChangeTags][com.onesignal.common.modeling.ModelChangeTags]-style values used only for
 * [ConfigModel] / [ConfigModelStore] replace notifications.
 */
internal object ConfigModelChangeTags {
    /**
     * A partial update from the Turbine feature-flags refresh: only
     * [ConfigModel.sdkRemoteFeatureFlags] and [ConfigModel.sdkRemoteFeatureFlagMetadata] changed
     * (in-place on the live model, not a full [ConfigModel] replace).
     */
    const val REMOTE_FEATURE_FLAGS = "REMOTE_FEATURE_FLAGS"
}
