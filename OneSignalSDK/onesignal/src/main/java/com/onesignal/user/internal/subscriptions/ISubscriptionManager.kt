package com.onesignal.user.internal.subscriptions

import com.onesignal.common.events.IEventNotifier
import com.onesignal.user.subscriptions.SubscriptionList

interface ISubscriptionManager : IEventNotifier<ISubscriptionChangedHandler> {
    var subscriptions: SubscriptionList

    fun addEmailSubscription(email: String)
    fun addOrUpdatePushSubscription(pushToken: String?, pushTokenStatus: Int?)
    fun addSmsSubscription(sms: String)
    fun removeEmailSubscription(email: String)
    fun removeSmsSubscription(sms: String)
}

interface ISubscriptionChangedHandler {
    fun onSubscriptionsChanged()
}
