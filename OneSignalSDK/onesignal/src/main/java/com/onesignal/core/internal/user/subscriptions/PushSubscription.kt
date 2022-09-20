package com.onesignal.core.internal.user.subscriptions

import com.onesignal.core.internal.models.SubscriptionModel
import com.onesignal.core.user.subscriptions.IPushSubscription

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
