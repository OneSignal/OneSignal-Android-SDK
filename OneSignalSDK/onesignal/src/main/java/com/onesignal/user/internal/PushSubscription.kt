package com.onesignal.user.internal

import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.subscriptions.IPushSubscription

internal class PushSubscription(
    model: SubscriptionModel
) : Subscription(model), IPushSubscription {

    override val pushToken: String?
        get() = model.address

    override var enabled: Boolean
        get() = model.enabled
        set(value) { model.enabled = value }

    private var _enabled: Boolean = enabled
}
