package com.onesignal.core.internal.config.impl

import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.core.internal.config.ConfigModelChangeTags
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.backend.IFeatureFlagsBackendService
import com.onesignal.core.internal.backend.impl.FeatureFlagsJsonParser
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.debug.internal.logging.Logging
import kotlin.coroutines.coroutineContext
import kotlin.jvm.Volatile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Fetches remote SDK feature flags when the app is in the foreground, immediately on focus and then
 * every [REFRESH_INTERVAL_MS] while the session stays in the foreground. Updates
 * [ConfigModel.sdkRemoteFeatureFlags] / [ConfigModel.sdkRemoteFeatureFlagMetadata] so
 * [com.onesignal.core.internal.features.FeatureManager] stays in sync.
 *
 * Remote fields are updated in place on the live [ConfigModel] (with [ConfigModelChangeTags.REMOTE_FEATURE_FLAGS])
 * so concurrent hydration cannot be overwritten by a stale full-model snapshot.
 */
internal class FeatureFlagsRefreshService(
    private val applicationService: IApplicationService,
    private val configModelStore: ConfigModelStore,
    private val featureFlagsBackend: IFeatureFlagsBackendService,
) : IStartableService,
    IApplicationLifecycleHandler,
    ISingletonModelStoreChangeHandler<ConfigModel> {
    @Volatile
    private var pollJob: Job? = null

    override fun start() {
        applicationService.addApplicationLifecycleHandler(this)
        configModelStore.subscribe(this)
        // Foreground-at-subscribe is handled by [IApplicationService.addApplicationLifecycleHandler] (fires onFocus).
    }

    override fun onFocus(firedOnSubscribe: Boolean) {
        restartForegroundPolling()
    }

    override fun onUnfocused() {
        pollJob?.cancel()
        pollJob = null
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
        if (tag == ConfigModelChangeTags.REMOTE_FEATURE_FLAGS) {
            return
        }
        if (tag != ModelChangeTags.HYDRATE && tag != ModelChangeTags.NORMAL) {
            return
        }
        if (model.appId.isNotEmpty() && applicationService.isInForeground) {
            restartForegroundPolling()
        }
    }

    private fun restartForegroundPolling() {
        pollJob?.cancel()
        pollJob =
            OneSignalDispatchers.launchOnIO {
                while (coroutineContext.isActive) {
                    if (!applicationService.isInForeground) {
                        break
                    }
                    val appId = configModelStore.model.appId
                    if (appId.isNotEmpty()) {
                        try {
                            fetchAndApply(appId)
                        } catch (t: Throwable) {
                            Logging.warn("FeatureFlagsRefreshService: fetch failed", t)
                        }
                    }
                    delay(REFRESH_INTERVAL_MS)
                }
            }
    }

    private suspend fun fetchAndApply(appId: String) {
        val result = featureFlagsBackend.fetchRemoteFeatureFlags(appId)
        val current = configModelStore.model
        val newMetaString = FeatureFlagsJsonParser.encodeMetadata(result.metadata)
        if (result.enabledKeys.toSet() == current.sdkRemoteFeatureFlags.toSet() &&
            newMetaString == current.sdkRemoteFeatureFlagMetadata
        ) {
            return
        }

        val tag = ConfigModelChangeTags.REMOTE_FEATURE_FLAGS
        current.setListProperty(ConfigModel::sdkRemoteFeatureFlags.name, result.enabledKeys, tag)
        current.setOptStringProperty(ConfigModel::sdkRemoteFeatureFlagMetadata.name, newMetaString, tag)
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 600_000L
    }
}
