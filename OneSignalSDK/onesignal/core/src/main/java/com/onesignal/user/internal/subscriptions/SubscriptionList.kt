package com.onesignal.user.internal.subscriptions

import com.onesignal.common.PIIHasher
import com.onesignal.user.internal.Subscription
import com.onesignal.user.subscriptions.IEmailSubscription
import com.onesignal.user.subscriptions.IPushSubscription
import com.onesignal.user.subscriptions.ISmsSubscription
import com.onesignal.user.subscriptions.ISubscription

/**
 * A readonly list of subscriptions.  Wraps a standard [List] to help navigate the list of
 * subscriptions.  The full list can be accessed via [SubscriptionList.collection].
 */
class SubscriptionList(val collection: List<ISubscription>, private val _fallbackPushSub: IPushSubscription) {
    /**
     * Retrieve the push subscription for this user on the current device.
     */
    val push: IPushSubscription
        get() = collection.filterIsInstance<IPushSubscription>().firstOrNull() ?: _fallbackPushSub

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
     * Compares against the underlying model address (raw or hashed) so lookups
     * work both before and after server hydration.
     */
    fun getByEmail(email: String): IEmailSubscription? {
        val hashed = PIIHasher.hash(email)
        return emails.firstOrNull {
            val address = (it as Subscription).model.address
            address == email || address == hashed
        }
    }

    /**
     * Retrieve the SMS subscription with the matching SMS number, if there is one.
     * Compares against the underlying model address (raw or hashed) so lookups
     * work both before and after server hydration.
     */
    fun getBySMS(sms: String): ISmsSubscription? {
        val hashed = PIIHasher.hash(sms)
        return smss.firstOrNull {
            val address = (it as Subscription).model.address
            address == sms || address == hashed
        }
    }
}
