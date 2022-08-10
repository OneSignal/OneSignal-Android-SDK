package com.onesignal.onesignal.core.internal.user.subscriptions

import com.onesignal.onesignal.core.internal.user.ISubscriptionManager
import com.onesignal.onesignal.core.user.subscriptions.IPushSubscription
import java.util.*

internal class PushSubscription(
        id: UUID,
        enabled: Boolean,
        override val pushToken: String,
        private val _subscriptionManager: ISubscriptionManager
        ) : Subscription(id), IPushSubscription {

    override var enabled: Boolean
        get() = _enabled
        set(value) {
            _enabled = value
            _subscriptionManager.setSubscriptionEnablement(this, value)
        }

    private var _enabled: Boolean = enabled
}