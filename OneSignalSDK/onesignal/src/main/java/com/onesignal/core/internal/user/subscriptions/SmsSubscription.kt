package com.onesignal.core.internal.user.subscriptions

import com.onesignal.core.internal.models.SubscriptionModel
import com.onesignal.core.user.subscriptions.ISmsSubscription

internal class SmsSubscription(
    model: SubscriptionModel
) : Subscription(model), ISmsSubscription {
    override val number: String
        get() = model.address
}
