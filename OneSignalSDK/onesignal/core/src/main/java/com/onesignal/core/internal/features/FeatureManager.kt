package com.onesignal.core.internal.features

import android.content.Context
import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.features.DeferredFeatureActivation
import com.onesignal.features.FeatureFlag
import com.onesignal.features.FeatureFlagMetadata
import com.onesignal.features.StartupFlags
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
     * Values are JSON object text (flag id → object). `null` when nothing has been
     * stored yet.
     */
    fun remoteFeatureFlagMetadata(): Map<String, String>?
}

/**
 * Android host for the shared [SharedFeatureManager]: owns [ConfigModel] persistence, the startup
 * flags file, and store subscriptions. Latching lives in KMP. See README.md in this package.
 */
@Suppress("TooGenericExceptionCaught")
internal class FeatureManager(
    private val configModelStore: ConfigModelStore,
    private val applicationService: IApplicationService,
) : IFeatureManager, IStartableService, ISingletonModelStoreChangeHandler<ConfigModel> {
    private val latch = SharedFeatureManager()

    // Writes wait for start() and run on the caller's thread, so staying off main is caller-dependent:
    // StartupService runs start() on the default lane, and the flag fetches update ConfigModel on IO.
    @Volatile
    private var started = false

    init {
        Logging.debug("OneSignal: FeatureManager initializing from cached config features")
        if (localFeatureOverrides.isNotEmpty()) {
            Logging.warn("OneSignal: Local feature override enabled for testing only: $localFeatureOverrides")
        }
        // APP_STARTUP flags come from the snapshot pre-init code also latched from, so telemetry
        // never reports a startup flag the running process is not using.
        val startup = StartupFlagsCache.snapshot { appContextOrNull() }
        Logging.debug("OneSignal: startup flags source=${startup.source} enabled=${startup.keys}")
        try {
            logDeferred(latch.refreshAtStartup(configModelStore.model.sdkRemoteFeatureFlags, startup, localFeatureOverrides))
        } catch (t: Throwable) {
            Logging.error("OneSignal: Failed to initialize feature states from cached config", t)
            latch.refreshAtStartup(emptyList(), startup, localFeatureOverrides)
        }
        configModelStore.subscribe(this)
    }

    override fun start() {
        started = true
        mirrorStartupFlags()
    }

    override fun isEnabled(feature: FeatureFlag): Boolean = latch.isEnabled(feature)

    override fun enabledFeatureKeys(): List<String> = latch.enabledFeatureKeys()

    override fun remoteFeatureFlagMetadata(): Map<String, String>? {
        val parsed =
            FeatureFlagMetadata.parse(
                configModelStore.model.sdkRemoteFeatureFlagMetadata,
            ) ?: return null
        return parsed.ids().associateWith { id -> parsed.jsonObjectForId(id).orEmpty() }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun onModelReplaced(
        model: ConfigModel,
        tag: String,
    ) {
        Logging.debug("OneSignal: FeatureManager.onModelReplaced(tag=$tag)")
        if (started) mirrorStartupFlags()
        if (tag == ModelChangeTags.HYDRATE || tag == ModelChangeTags.NORMAL) {
            try {
                refreshFrom(model)
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
            if (started && args.property == ConfigModel::sdkRemoteFeatureFlags.name) {
                mirrorStartupFlags()
            }
            try {
                refreshFrom(configModelStore.model)
            } catch (t: Throwable) {
                Logging.error("OneSignal: Failed to refresh features on model update", t)
            }
        }
    }

    private fun refreshFrom(model: ConfigModel) {
        logDeferred(latch.refresh(model.sdkRemoteFeatureFlags, false, localFeatureOverrides))
    }

    private fun logDeferred(deferred: List<DeferredFeatureActivation>) {
        for (change in deferred) {
            Logging.info(
                "OneSignal: Feature ${change.key} changed remotely to ${change.desiredEnabled} " +
                    "but is NEXT_RUN, keeping current run value=${change.latchedEnabled}",
            )
        }
    }

    // Reads the store's model inside the write lock: a list captured by the caller could be stale.
    private fun mirrorStartupFlags() {
        try {
            StartupFlagsCache.write(applicationService.appContext) { configModelStore.model.sdkRemoteFeatureFlags }
        } catch (t: Throwable) {
            Logging.warn("OneSignal: failed to mirror startup flags: ${t.message}", t)
        }
    }

    @Suppress("SwallowedException")
    private fun appContextOrNull(): Context? =
        try {
            applicationService.appContext
        } catch (t: Throwable) {
            null
        }

    companion object {
        /** APP_STARTUP flags for code that runs before SDK init. Same values [isEnabled] reports. */
        fun startupFlags(context: () -> Context?): StartupFlags = StartupFlagsCache.snapshot(context)

        /** Forgets this process's startup flags, as a new process would. */
        internal fun resetStartupFlagsForTest() = StartupFlagsCache.setSnapshotForTest(null)

        /**
         * Local-only test hook for forcing features ON without backend config.
         * Add feature keys here while testing locally, e.g.
         * `listOf(FeatureFlag.SDK_IDENTITY_VERIFICATION.key)`.
         */
        private val localFeatureOverrides: List<String> = emptyList()
    }
}
