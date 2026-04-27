package com.onesignal.core.internal.config.impl

import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.backend.IFeatureFlagsBackendService
import com.onesignal.core.internal.backend.RemoteFeatureFlagsFetchOutcome
import com.onesignal.core.internal.backend.impl.FeatureFlagsJsonParser
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelChangeTags
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Fetches remote SDK feature flags when the app is in the foreground, immediately on focus and then
 * every [refreshIntervalMs] while the session stays in the foreground. Updates
 * [ConfigModel.sdkRemoteFeatureFlags] / [ConfigModel.sdkRemoteFeatureFlagMetadata] so
 * [com.onesignal.core.internal.features.FeatureManager] stays in sync.
 *
 * Remote fields are updated in place on the live [ConfigModel] (with [ConfigModelChangeTags.REMOTE_FEATURE_FLAGS])
 * so concurrent hydration cannot be overwritten by a stale full-model snapshot.
 *
 * Polling is keyed on the active [ConfigModel.appId]: once a poll loop is running for a given
 * appId, redundant triggers (e.g. [com.onesignal.common.modeling.ModelChangeTags.HYDRATE] replaces
 * from [ConfigModelStoreListener.fetchParams] that write the same appId back) are a no-op so we
 * don't double-fire the Turbine GET at startup. Genuine appId changes still cancel and restart.
 *
 * IMPORTANT: Constructor parameters must remain limited to types the IoC reflection can resolve
 * via [com.onesignal.common.services.IServiceProvider] (registered services or `List<Service>`).
 * Configuration knobs like [refreshIntervalMs] live as `internal var` instead so reflection in
 * [com.onesignal.common.services.ServiceRegistrationReflection.resolve] still picks the only
 * constructor and tests can still override the value before [start].
 */
internal class FeatureFlagsRefreshService(
    private val applicationService: IApplicationService,
    private val configModelStore: ConfigModelStore,
    private val featureFlagsBackend: IFeatureFlagsBackendService,
) : IStartableService,
    IApplicationLifecycleHandler,
    ISingletonModelStoreChangeHandler<ConfigModel> {
    /**
     * Foreground polling cadence; defaults to [DEFAULT_REFRESH_INTERVAL_MS] (8 minutes). Test-only
     * override (must be set before [start] / focus) – keep out of the constructor so the IoC's
     * reflection-based resolver doesn't trip on a non-service `Long` parameter (see class KDoc).
     */
    internal var refreshIntervalMs: Long = DEFAULT_REFRESH_INTERVAL_MS

    private var pollJob: Job? = null

    /**
     * AppId currently being polled. Used to dedup redundant `restartForegroundPolling` triggers
     * (e.g. HYDRATE replace from [ConfigModelStoreListener.fetchParams] that doesn't change the
     * appId). Cleared in [onUnfocused] so the next focus event always re-arms polling.
     */
    private var pollingAppId: String? = null

    override fun start() {
        applicationService.addApplicationLifecycleHandler(this)
        configModelStore.subscribe(this)
        // Foreground-at-subscribe is handled by [IApplicationService.addApplicationLifecycleHandler] (fires onFocus).
    }

    override fun onFocus(firedOnSubscribe: Boolean) {
        restartForegroundPolling()
    }

    override fun onUnfocused() {
        synchronized(this) {
            pollJob?.cancel()
            pollJob = null
            pollingAppId = null
        }
    }

    override fun onModelUpdated(
        args: ModelChangedArgs,
        tag: String,
    ) {
        if (args.property != ConfigModel::appId.name) {
            return
        }
        if (applicationService.isInForeground) {
            restartForegroundPolling()
        }
    }

    override fun onModelReplaced(
        model: ConfigModel,
        tag: String,
    ) {
        if (tag != ModelChangeTags.HYDRATE && tag != ModelChangeTags.NORMAL) {
            return
        }
        if (model.appId.isNotEmpty() && applicationService.isInForeground) {
            restartForegroundPolling()
        }
    }

    private fun restartForegroundPolling() {
        synchronized(this) {
            val appId = configModelStore.model.appId
            if (appId.isEmpty()) {
                pollJob?.cancel()
                pollJob = null
                pollingAppId = null
                return
            }
            // Dedup: an active loop for the same appId is already covering us.
            if (pollingAppId == appId) {
                return
            }
            pollJob?.cancel()
            pollingAppId = appId
            pollJob =
                OneSignalDispatchers.launchOnIO {
                    while (coroutineContext.isActive) {
                        if (!applicationService.isInForeground) {
                            break
                        }
                        val current = configModelStore.model.appId
                        if (current.isNotEmpty()) {
                            try {
                                fetchAndApply(current)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Logging.warn("FeatureFlagsRefreshService: fetch failed", e)
                            }
                        }
                        delay(refreshIntervalMs)
                    }
                }
        }
    }

    private suspend fun fetchAndApply(appId: String) {
        val result =
            when (val outcome = featureFlagsBackend.fetchRemoteFeatureFlags(appId)) {
                RemoteFeatureFlagsFetchOutcome.Unavailable -> return
                is RemoteFeatureFlagsFetchOutcome.Success -> outcome.result
            }
        val current = configModelStore.model
        val newMetaString = FeatureFlagsJsonParser.encodeMetadata(result.metadata)
        val beforeKeys = current.sdkRemoteFeatureFlags.toSet()
        val afterKeys = result.enabledKeys.toSet()
        if (afterKeys == beforeKeys && newMetaString == current.sdkRemoteFeatureFlagMetadata) {
            return
        }

        val tag = ConfigModelChangeTags.REMOTE_FEATURE_FLAGS
        current.setListProperty(ConfigModel::sdkRemoteFeatureFlags.name, result.enabledKeys, tag)
        current.setOptStringProperty(ConfigModel::sdkRemoteFeatureFlagMetadata.name, newMetaString, tag)
    }

    companion object {
        // Foreground polling cadence for remote feature flags (8 minutes).
        private const val DEFAULT_REFRESH_INTERVAL_MS = 480_000L
    }
}
