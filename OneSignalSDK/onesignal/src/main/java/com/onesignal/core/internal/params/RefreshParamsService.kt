package com.onesignal.core.internal.params

import com.onesignal.core.LogLevel
import com.onesignal.core.internal.backend.IParamsBackendService
import com.onesignal.core.internal.common.suspendifyOnThread
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.core.internal.models.ConfigModel
import com.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.user.ISubscriptionManager

/**
 * This is responsible for refreshing the parameters of the current OneSignal appId, whenever
 * required.  A refresh is required when:
 *
 * 1. The SDK has been started and an appId first assigned.
 * 2. The SDK has had it's appId changed.
 */
internal class RefreshParamsService(
    private val _configModelStore: ConfigModelStore,
    private val _paramsBackendService: IParamsBackendService,
    private val _subscriptionManager: ISubscriptionManager
) : IStartableService, ISingletonModelStoreChangeHandler<ConfigModel> {

    override fun start() {
        _configModelStore.subscribe(this)
        fetchParams()
    }

    override fun onModelUpdated(model: ConfigModel, property: String, oldValue: Any?, newValue: Any?) {
        if (property != ConfigModel::appId.name)
            return

        fetchParams()
    }

    private fun fetchParams() {
        val appId = _configModelStore.get().appId

        if (appId == null || appId.isEmpty())
            return

        suspendifyOnThread {
            Logging.log(LogLevel.DEBUG, "StartupService fetching parameters for appId: $appId")

            _paramsBackendService.fetchAndSaveRemoteParams(appId, _subscriptionManager.subscriptions.push?.id.toString())
        }
    }
}
