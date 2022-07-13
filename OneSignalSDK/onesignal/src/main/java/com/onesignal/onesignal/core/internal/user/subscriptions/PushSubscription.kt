package com.onesignal.onesignal.core.internal.user.subscriptions

import com.onesignal.onesignal.core.internal.user.UserManager
import com.onesignal.onesignal.core.user.subscriptions.IPushSubscription
import java.util.*

class PushSubscription(
        id: UUID,
        enabled: Boolean,
        override val pushToken: String,
        private val _userManager: UserManager
        ) : Subscription(id), IPushSubscription {

    override var enabled: Boolean
        get() = _enabled
        set(value) {
            _enabled = value
            _userManager.setSubscriptionEnablement(this, value)
        }

    private var _enabled: Boolean = enabled
}