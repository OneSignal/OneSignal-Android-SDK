package com.onesignal.core.internal.user.subscriptions

import com.onesignal.core.internal.models.SubscriptionModel
import com.onesignal.core.user.subscriptions.IEmailSubscription

internal class EmailSubscription(
    model: SubscriptionModel
) : Subscription(model), IEmailSubscription {
    override val email: String
        get() = model.address
}
