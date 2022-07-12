package com.onesignal.onesignal.core.user.subscriptions

/**
 * A readonly list of subscriptions.  Provides convenience methods on top of a standard list
 * to help navigate the list of subscriptions.
 */
class SubscriptionList(val collection: List<ISubscription>) {
    /**
     * Retrieve the subset of subscriptions that are [IPushSubscription].
     */
    val pushSubscriptions: List<IPushSubscription>
        get() = collection.filterIsInstance<IPushSubscription>()

    /**
     * Retrieve the subset of subscriptions that are [IEmailSubscription].
     */
    val emailSubscriptions: List<IEmailSubscription>
        get() = collection.filterIsInstance<IEmailSubscription>()

    /**
     * Retrieve the subset of subscriptions that are [ISmsSubscription].
     */
    val smsSubscriptions: List<ISmsSubscription>
        get() = collection.filterIsInstance<ISmsSubscription>()

    /**
     * Retrieve the subscription for this device, if there is one.
     */
    val thisDevice: IPushSubscription?
        get() = pushSubscriptions.firstOrNull() { it.isThisDevice }

    /**
     * Retrieve the Email subscription with the matching email, if there is one.
     */
    fun getByEmail(email: String) : IEmailSubscription? {
         return emailSubscriptions.firstOrNull { it.email == email }
    }

    /**
     * Retrieve the SMS subscription with the matching SMS number, if there is one.
     */
    fun getBySMS(sms: String) : ISmsSubscription? {
        return smsSubscriptions.firstOrNull { it.number == sms }
    }
}