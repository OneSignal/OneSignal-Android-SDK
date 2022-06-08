package com.onesignal.user.subscriptions

import com.onesignal.user.collections.OSCollection

class SubscriptionCollection(subscriptions: List<Subscription>) : OSCollection<Subscription>(subscriptions) {
    val push: Collection<PushSubscription>
        get() = collection.filterIsInstance<PushSubscription>()
    val email: Collection<EmailSubscription>
        get() = collection.filterIsInstance<EmailSubscription>()
    val sms: Collection<SmsSubscription>
        get() = collection.filterIsInstance<SmsSubscription>()
}