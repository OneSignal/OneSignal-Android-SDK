package com.onesignal.user.internal

import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.subscriptions.IEmailSubscription

internal class EmailSubscription(
    model: SubscriptionModel
) : Subscription(model), IEmailSubscription {
    override val email: String
        get() = model.address
}
