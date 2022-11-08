package com.onesignal.notifications.internal.listeners

import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.notifications.internal.INotificationStateRefresher
import com.onesignal.notifications.internal.pushtoken.IPushTokenChangedHandler
import com.onesignal.notifications.internal.pushtoken.IPushTokenManager
import com.onesignal.notifications.internal.registration.IPushRegistrator
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
