package com.onesignal.user.internal.subscriptions

import com.onesignal.common.events.IEventNotifier
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.user.subscriptions.ISubscription

interface ISubscriptionManager : IEventNotifier<ISubscriptionChangedHandler> {
    var subscriptions: SubscriptionList

    val pushSubscriptionModel: SubscriptionModel

    fun addEmailSubscription(email: String)

    fun addOrUpdatePushSubscription(
        pushToken: String?,
        pushTokenStatus: SubscriptionStatus,
    )

    fun addSmsSubscription(sms: String)

    fun removeEmailSubscription(email: String)

    fun removeSmsSubscription(sms: String)
}

interface ISubscriptionChangedHandler {
    fun onSubscriptionAdded(subscription: ISubscription)

    fun onSubscriptionChanged(
        subscription: ISubscription,
        args: ModelChangedArgs,
    )

    fun onSubscriptionRemoved(subscription: ISubscription)
}
