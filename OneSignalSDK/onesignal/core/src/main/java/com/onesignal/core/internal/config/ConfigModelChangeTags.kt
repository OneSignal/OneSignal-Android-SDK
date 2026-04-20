package com.onesignal.core.internal.config

/**
 * [ModelChangeTags][com.onesignal.common.modeling.ModelChangeTags]-style values used only for
 * [ConfigModel] / [ConfigModelStore] replace notifications.
 */
internal object ConfigModelChangeTags {
    /**
     * Remote feature-flags HTTP endpoint updated [ConfigModel] SDK flag fields only
     * ([ConfigModel.sdkRemoteFeatureFlags], [ConfigModel.sdkRemoteFeatureFlagMetadata]).
     */
    const val REMOTE_FEATURE_FLAGS = "REMOTE_FEATURE_FLAGS"
}
