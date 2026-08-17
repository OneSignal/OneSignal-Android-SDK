package com.onesignal.core.internal.features

import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.features.FeatureFlag
import com.onesignal.features.FeatureFlagsJsonParser
import kotlinx.serialization.json.JsonObject
import com.onesignal.features.FeatureManager as SharedFeatureManager

/**
 * Resolves backend-driven [FeatureFlag] state for the current device run.
 * Catalog and latching live in shared [SharedFeatureManager]; this host hydrates
 * from [ConfigModel] and applies [com.onesignal.features.FeatureActivationMode]
 * rules via [SharedFeatureManager.refresh].
 */
interface IFeatureManager {
    /**
     * Whether [feature] is enabled for the current run, after remote config and
     * [com.onesignal.features.FeatureActivationMode] latching.
     */
    fun isEnabled(feature: FeatureFlag): Boolean

    /**
     * Canonical keys enabled for this process after latching.
     * Order follows [FeatureFlag] declaration order.
     */
    fun enabledFeatureKeys(): List<String>

    /**
     * Per-flag payloads persisted on [ConfigModel.sdkRemoteFeatureFlagMetadata].
     * `null` when nothing has been stored yet.
     */
    fun remoteFeatureFlagMetadata(): Map<String, JsonObject>?
}

/**
 * Android host for the shared [SharedFeatureManager] latch.
 *
 * Persistence ([ConfigModel]) and store subscriptions stay here. Catalog, activation
 * modes, and isEnabled latching live in KMP so iOS can call the same
 * [SharedFeatureManager.refresh] with cached keys at process start
 * (`applyAppStartupFlags = true`) and again after later fetches (`false`).
 *
 * KMP [SharedFeatureManager] is not itself synchronized (`@Volatile` is unavailable on
 * Kotlin/Native 1.9). This host serializes [isEnabled] / refresh on [latchLock].
 * iOS should do the same with a serial queue around the shared latch.
 */
@Suppress("TooGenericExceptionCaught")
internal class FeatureManager(
    private val configModelStore: ConfigModelStore,
) : IFeatureManager, ISingletonModelStoreChangeHandler<ConfigModel> {
    private val latchLock = Any()
    private val latch = SharedFeatureManager()

    init {
        Logging.debug("OneSignal: FeatureManager initializing from cached config features")
        try {
            refreshFrom(configModelStore.model, applyAppStartupFlags = true)
        } catch (t: Throwable) {
            Logging.error("OneSignal: Failed to initialize feature states from cached config", t)
        }
        configModelStore.subscribe(this)
    }

    override fun isEnabled(feature: FeatureFlag): Boolean =
        synchronized(latchLock) { latch.isEnabled(feature) }

    override fun enabledFeatureKeys(): List<String> =
        synchronized(latchLock) { latch.enabledFeatureKeys() }

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
        if (tag == ModelChangeTags.HYDRATE || tag == ModelChangeTags.NORMAL) {
            try {
                refreshFrom(model, applyAppStartupFlags = false)
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
        if (args.property == ConfigModel::sdkRemoteFeatureFlags.name ||
            args.property == ConfigModel::sdkRemoteFeatureFlagMetadata.name
        ) {
            Logging.debug("OneSignal: FeatureManager.onModelUpdated(property=${args.property}, tag=$tag)")
            try {
                refreshFrom(configModelStore.model, applyAppStartupFlags = false)
            } catch (t: Throwable) {
                Logging.error("OneSignal: Failed to refresh features on model update", t)
            }
        }
    }

    private fun refreshFrom(
        model: ConfigModel,
        applyAppStartupFlags: Boolean,
    ) {
        if (localFeatureOverrides.isNotEmpty()) {
            Logging.warn(
                "OneSignal: Local feature override enabled for testing only: $localFeatureOverrides",
            )
        }
        val deferred =
            synchronized(latchLock) {
                latch.refresh(model.sdkRemoteFeatureFlags, applyAppStartupFlags, localFeatureOverrides)
            }
        for (change in deferred) {
            Logging.info(
                "OneSignal: Feature ${change.key} changed remotely to ${change.desiredEnabled} " +
                    "but is NEXT_RUN, keeping current run value=${change.latchedEnabled}",
            )
        }
    }

    companion object {
        /**
         * Local-only test hook for forcing features ON without backend config.
         * Add feature keys here while testing locally, e.g.
         * `listOf(FeatureFlag.SDK_IDENTITY_VERIFICATION.key)`.
         */
        private val localFeatureOverrides: List<String> = emptyList()
    }
}
