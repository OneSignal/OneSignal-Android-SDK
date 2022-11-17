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

    /**
     * Add a change handler to this subscription, allowing the provider to be
     * notified whenever the subscription has changed.
     */
    fun addChangeHandler(handler: ISubscriptionChangedHandler)

    /**
     * Remove a change handler from this subscription.
     */
    fun removeChangeHandler(handler: ISubscriptionChangedHandler)
}
