package com.onesignal.onesignal.core.internal.startup

import com.onesignal.onesignal.core.LogLevel
import com.onesignal.onesignal.core.internal.backend.IParamsBackendService
import com.onesignal.onesignal.core.internal.common.suspendifyOnThread
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.onesignal.core.internal.models.ConfigModel
import com.onesignal.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.onesignal.core.internal.user.ISubscriptionManager

internal class StartupService(
    private val _paramsBackendService: IParamsBackendService,
    private val _configModelStore: ConfigModelStore,
    private val _subscriptionManager: ISubscriptionManager,
    private val _startableServices: List<IStartableService>,
) : ISingletonModelStoreChangeHandler<ConfigModel> {

    fun start() {
        _configModelStore.subscribe(this)

        // now that we have the params initialized, start everything else up
        for(startableService in _startableServices)
            startableService.start()
    }

    override fun onModelUpdated(model: ConfigModel, property: String, oldValue: Any?, newValue: Any?) {
        if (property != ConfigModel::appId.name)
            return

        val appId = model.appId

        if(appId == null || appId.isEmpty())
            return

        suspendifyOnThread {
            Logging.log(LogLevel.DEBUG, "StartupService fetching parameters for appId: $appId")

            _paramsBackendService.fetchAndSaveRemoteParams(appId, _subscriptionManager.subscriptions.push?.id.toString())
        }
    }
}