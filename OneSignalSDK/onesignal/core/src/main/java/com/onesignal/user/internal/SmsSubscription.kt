package com.onesignal.user.internal

import com.onesignal.common.PIIHasher
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.subscriptions.ISmsSubscription

internal class SmsSubscription(
    model: SubscriptionModel,
) : Subscription(model), ISmsSubscription {
    override val number: String
        get() {
            val address = model.address
            return if (PIIHasher.isHashed(address)) "" else address
        }
}
