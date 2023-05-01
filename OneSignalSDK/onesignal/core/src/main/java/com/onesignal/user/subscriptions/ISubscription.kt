package com.onesignal.user.subscriptions

/**
 * A subscription
 */
interface ISubscription {
    /**
     * The unique identifier for this subscription. This will be an empty string
     * until the subscription has been successfully created on the backend and
     * assigned an ID.  Use [addChangeHandler] to be notified when the [id] has
     * been successfully assigned.
     */
    val id: String
}
