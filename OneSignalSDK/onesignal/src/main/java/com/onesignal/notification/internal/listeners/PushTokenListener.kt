package com.onesignal.notification.internal.listeners

import com.onesignal.core.internal.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.core.internal.models.ConfigModel
import com.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.core.internal.models.SubscriptionType
import com.onesignal.core.internal.operations.CreateAndAddSubscriptionOperation
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.user.ISubscriptionManager
import com.onesignal.notification.internal.INotificationStateRefresher
import com.onesignal.notification.internal.pushtoken.IPushTokenChangedHandler
import com.onesignal.notification.internal.pushtoken.IPushTokenManager

internal class PushTokenListener(
    private val _operationRepo: IOperationRepo,
    private val _configModelStore: ConfigModelStore,
    private val _pushTokenManager: IPushTokenManager,
    private val _subscriptionManager: ISubscriptionManager,
    private val _notificationStateRefresher: INotificationStateRefresher
) : IStartableService, IPushTokenChangedHandler, ISingletonModelStoreChangeHandler<ConfigModel> {

    override fun start() {
        _pushTokenManager.subscribe(this)
        _configModelStore.subscribe(this)
    }

    override fun onModelUpdated(model: ConfigModel, property: String, oldValue: Any?, newValue: Any?) {
        if (property != ConfigModel::appId.name) {
            return
        }

        createSubscriptionIfRequired()
    }

    override fun onPushTokenChanged(pushToken: String?) {
        if (pushToken == null || pushToken.isEmpty()) {
            return
        }

        createSubscriptionIfRequired()
        _notificationStateRefresher.refreshNotificationState()
    }

    private fun createSubscriptionIfRequired() {
        val appId = _configModelStore.get().appId
        val pushToken = _pushTokenManager.pushToken

        if (appId == null || pushToken == null || _subscriptionManager.subscriptions.push != null) {
            return
        }

        // TODO: Is is possible for the push token to change and we need to update a subscription?
        _operationRepo.enqueue(CreateAndAddSubscriptionOperation(appId, SubscriptionType.PUSH, true, pushToken))
    }
}
