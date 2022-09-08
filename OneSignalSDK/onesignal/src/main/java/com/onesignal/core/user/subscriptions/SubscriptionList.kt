package com.onesignal.core.user.subscriptions

/**
 * A readonly list of subscriptions.  Wraps a standard [List] to help navigate the list of
 * subscriptions.  The full list can be accessed via [SubscriptionList.collection].
 */
class SubscriptionList(val collection: List<ISubscription>) {

    /**
     * Retrieve the push subscription for this user.  This subscription is for this
     * device, or it does not exist and is null.
     */
    val push: IPushSubscription?
        get() = collection.filterIsInstance<IPushSubscription>().firstOrNull()

    /**
     * Retrieve the subset of subscriptions that are [IEmailSubscription].
     */
    val emails: List<IEmailSubscription>
        get() = collection.filterIsInstance<IEmailSubscription>()

    /**
     * Retrieve the subset of subscriptions that are [ISmsSubscription].
     */
    val smss: List<ISmsSubscription>
        get() = collection.filterIsInstance<ISmsSubscription>()

    /**
     * Retrieve the Email subscription with the matching email, if there is one.
     */
    fun getByEmail(email: String): IEmailSubscription? {
        return emails.firstOrNull { it.email == email }
    }

    /**
     * Retrieve the SMS subscription with the matching SMS number, if there is one.
     */
    fun getBySMS(sms: String): ISmsSubscription? {
        return smss.firstOrNull { it.number == sms }
    }
}
