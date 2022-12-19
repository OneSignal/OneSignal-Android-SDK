package com.onesignal.user.internal

import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.internal.subscriptions.SubscriptionType
import com.onesignal.user.subscriptions.IPushSubscription

internal open class PushSubscription(
    model: SubscriptionModel
) : Subscription(model), IPushSubscription {

    override val pushToken: String
        get() = model.address

    override val optedIn: Boolean
        get() = model.optedIn && model.status != SubscriptionStatus.NO_PERMISSION

    override fun optIn() {
        model.optedIn = true
    }

    override fun optOut() {
        model.optedIn = false
    }
}

internal class UninitializedPushSubscription() : PushSubscription(createFakePushSub()) {
    companion object {
        fun createFakePushSub(): SubscriptionModel {
            val pushSubModel = SubscriptionModel()
            pushSubModel.id = ""
            pushSubModel.type = SubscriptionType.PUSH
            pushSubModel.optedIn = false
            pushSubModel.address = ""
            return pushSubModel
        }
    }
}
