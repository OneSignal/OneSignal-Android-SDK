package com.onesignal.notification.internal.listeners

import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.notification.internal.INotificationStateRefresher
import com.onesignal.notification.internal.pushtoken.IPushTokenChangedHandler
import com.onesignal.notification.internal.pushtoken.IPushTokenManager
import com.onesignal.notification.internal.registration.IPushRegistrator
import com.onesignal.user.internal.subscriptions.ISubscriptionManager

internal class PushTokenListener(
    private val _pushTokenManager: IPushTokenManager,
    private val _subscriptionManager: ISubscriptionManager,
    private val _notificationStateRefresher: INotificationStateRefresher
) : IStartableService, IPushTokenChangedHandler {

    override fun start() {
        _pushTokenManager.subscribe(this)
    }

    override fun onPushTokenChanged(pushToken: String?, pushTokenStatus: IPushRegistrator.RegisterStatus) {
        _subscriptionManager.addOrUpdatePushSubscription(pushToken, pushTokenStatus.value)
        _notificationStateRefresher.refreshNotificationState()
    }
}
