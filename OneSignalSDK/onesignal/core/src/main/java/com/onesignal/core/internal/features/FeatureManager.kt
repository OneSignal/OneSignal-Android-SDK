package com.onesignal.core.internal.features

import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.common.threading.ThreadingMode
import com.onesignal.core.internal.backend.impl.FeatureFlagsJsonParser
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelChangeTags
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.debug.internal.logging.Logging
import kotlinx.serialization.json.JsonObject

internal interface IFeatureManager {
    fun isEnabled(feature: FeatureFlag): Boolean

    /**
     * Per-flag payloads from [com.onesignal.core.internal.backend.IFeatureFlagsBackendService].
     * Each value is a [JsonObject] so callers can decode nested fields or map to `@Serializable` types.
     *
     * `null` when no metadata has been stored yet ([ConfigModel.sdkRemoteFeatureFlagMetadata] null/blank).
     */
    fun remoteFeatureFlagMetadata(): Map<String, JsonObject>?
}

@Suppress("TooGenericExceptionCaught")
internal class FeatureManager(
    private val configModelStore: ConfigModelStore,
) : IFeatureManager, ISingletonModelStoreChangeHandler<ConfigModel> {
    @Volatile
    private var featureStates: Map<FeatureFlag, Boolean> = emptyMap()

    init {
        Logging.debug("OneSignal: FeatureManager initializing from cached config features")
        try {
            refreshEnabledFeatures(configModelStore.model, applyNextRunOnlyFeatures = true)
        } catch (t: Throwable) {
            Logging.error("OneSignal: Failed to initialize feature states from cached config", t)
        }
        configModelStore.subscribe(this)
    }

    override fun isEnabled(feature: FeatureFlag): Boolean = featureStates[feature] ?: false

    override fun remoteFeatureFlagMetadata(): Map<String, JsonObject>? {
        val raw = configModelStore.model.sdkRemoteFeatureFlagMetadata
        if (raw.isNullOrBlank()) {
            return null
        }
        return FeatureFlagsJsonParser.parseStoredMetadataMap(raw)
    }

    @Suppress("TooGenericExceptionCaught")
    override fun onModelReplaced(
        model: ConfigModel,
        tag: String,
    ) {
        Logging.debug("OneSignal: FeatureManager.onModelReplaced(tag=$tag)")
        if (tag == ModelChangeTags.HYDRATE ||
            tag == ModelChangeTags.NORMAL ||
            tag == ConfigModelChangeTags.REMOTE_FEATURE_FLAGS
        ) {
            try {
                refreshEnabledFeatures(model, applyNextRunOnlyFeatures = false)
            } catch (t: Throwable) {
                Logging.error("OneSignal: Failed to refresh features on model replace", t)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun onModelUpdated(
        args: ModelChangedArgs,
        tag: String,
    ) {
        if (args.property == ConfigModel::features.name ||
            args.property == ConfigModel::sdkRemoteFeatureFlags.name ||
            args.property == ConfigModel::sdkRemoteFeatureFlagMetadata.name
        ) {
            Logging.debug("OneSignal: FeatureManager.onModelUpdated(property=${args.property}, tag=$tag)")
            try {
                refreshEnabledFeatures(configModelStore.model, applyNextRunOnlyFeatures = false)
            } catch (t: Throwable) {
                Logging.error("OneSignal: Failed to refresh features on model update", t)
            }
        }
    }

    @Suppress("NestedBlockDepth")
    private fun refreshEnabledFeatures(
        model: ConfigModel,
        applyNextRunOnlyFeatures: Boolean,
    ) {
        val enabledFeatureKeys =
            (
                model.features +
                    model.sdkRemoteFeatureFlags +
                    localFeatureOverrides
            ).toSet()
        if (localFeatureOverrides.isNotEmpty()) {
            Logging.warn(
                "OneSignal: Local feature override enabled for testing only: $localFeatureOverrides",
            )
        }
        val nextStates = featureStates.toMutableMap()

        for (feature in FeatureFlag.entries) {
            val desiredState = feature.isEnabledIn(enabledFeatureKeys)
            when (feature.activationMode) {
                FeatureActivationMode.IMMEDIATE -> {
                    nextStates[feature] = desiredState
                    applySideEffects(feature, desiredState)
                }

                FeatureActivationMode.APP_STARTUP -> {
                    val hasBeenInitialized = nextStates.containsKey(feature)
                    if (applyNextRunOnlyFeatures || !hasBeenInitialized) {
                        nextStates[feature] = desiredState
                        applySideEffects(feature, desiredState)
                    } else {
                        val currentState = nextStates[feature] ?: false
                        if (currentState != desiredState) {
                            Logging.info(
                                "OneSignal: Feature ${feature.key} changed remotely to $desiredState " +
                                    "but is NEXT_RUN, keeping current run value=$currentState",
                            )
                        }
                    }
                }
            }
        }

        featureStates = nextStates
    }

    private fun applySideEffects(
        feature: FeatureFlag,
        enabled: Boolean,
    ) {
        when (feature) {
            FeatureFlag.SDK_BACKGROUND_THREADING ->
                ThreadingMode.updateUseBackgroundThreading(
                    enabled = enabled,
                    source = "FeatureManager:${feature.activationMode}"
                )
        }
    }

    companion object {
        /**
         * Local-only test hook for forcing features ON without backend config.
         * Add feature keys here while testing locally, e.g.:
         * setOf(FeatureFlag.SDK_BACKGROUND_THREADING.key)
         */
        private val localFeatureOverrides: Set<String> = emptySet()
//        private val localFeatureOverrides: Set<String> =
//            setOf(FeatureFlag.SDK_BACKGROUND_THREADING.key)
    }
}
