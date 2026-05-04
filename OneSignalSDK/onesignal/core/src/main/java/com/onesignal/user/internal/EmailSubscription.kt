package com.onesignal.user.internal

import com.onesignal.common.PIIHasher
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.subscriptions.IEmailSubscription

internal class EmailSubscription(
    model: SubscriptionModel,
) : Subscription(model), IEmailSubscription {
    override val email: String
        get() {
            val address = model.address
            return if (PIIHasher.isHashed(address)) "" else address
        }
}
