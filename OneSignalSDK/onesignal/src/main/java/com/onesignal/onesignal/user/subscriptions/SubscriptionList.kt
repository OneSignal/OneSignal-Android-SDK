package com.onesignal.onesignal.user.subscriptions

/**
 * A readonly list of subscriptions.  Provides convenience methods on top of a standard list
 * to help navigate the list of subscriptions.
 */
class SubscriptionList(val collection: List<Subscription>) {
    /**
     * Retrieve the subset of subscriptions that are [PushSubscription].
     */
    val push: List<PushSubscription>
        get() = collection.filterIsInstance<PushSubscription>()

    /**
     * Retrieve the subset of subscriptions that are [EmailSubscription].
     */
    val email: List<EmailSubscription>
        get() = collection.filterIsInstance<EmailSubscription>()

    /**
     * Retrieve the subset of subscriptions that are [SmsSubscription].
     */
    val sms: List<SmsSubscription>
        get() = collection.filterIsInstance<SmsSubscription>()

    /** Retrieve the subscription for this device, if there is one **/
    val onThisDevice: PushSubscription?
        get() = collection.filterIsInstance<PushSubscription>().firstOrNull() // TODO("Implement")
}