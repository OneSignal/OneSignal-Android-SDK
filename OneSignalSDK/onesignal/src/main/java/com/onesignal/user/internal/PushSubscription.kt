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

    override var enabled: Boolean
        get() = model.enabled && model.status == SubscriptionStatus.SUBSCRIBED
        set(value) { model.enabled = value }
}

internal class UninitializedPushSubscription() : PushSubscription(createFakePushSub()) {

    companion object {
        fun createFakePushSub(): SubscriptionModel {
            val pushSubModel = SubscriptionModel()
            pushSubModel.id = ""
            pushSubModel.type = SubscriptionType.PUSH
            pushSubModel.enabled = false
            return pushSubModel
        }
    }
}
