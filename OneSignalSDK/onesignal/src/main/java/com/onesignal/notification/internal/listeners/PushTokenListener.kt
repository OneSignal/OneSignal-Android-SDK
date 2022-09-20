package com.onesignal.notification.internal.listeners

import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.user.ISubscriptionManager
import com.onesignal.notification.internal.INotificationStateRefresher
import com.onesignal.notification.internal.pushtoken.IPushTokenChangedHandler
import com.onesignal.notification.internal.pushtoken.IPushTokenManager

internal class PushTokenListener(
    private val _pushTokenManager: IPushTokenManager,
    private val _subscriptionManager: ISubscriptionManager,
    private val _notificationStateRefresher: INotificationStateRefresher
) : IStartableService, IPushTokenChangedHandler {

    override fun start() {
        _pushTokenManager.subscribe(this)
    }

    override fun onPushTokenChanged(pushToken: String?) {
        if (pushToken == null || pushToken.isEmpty()) {
            return
        }

        _subscriptionManager.addOrUpdatePushSubscription(_pushTokenManager.pushToken)
        _notificationStateRefresher.refreshNotificationState()
    }
}
