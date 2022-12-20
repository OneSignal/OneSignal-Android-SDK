package com.onesignal.user.subscriptions

/**
 * A subscription changed handler. Implement this interface and provide the implementation
 * to [ISubscription.addChangeHandler] to be notified when the subscription has changed.
 */
interface ISubscriptionChangedHandler {

    /**
     * Called when the subscription this change handler was added to, has changed. A
     * subscription can change either because of a change driven by the application, or
     * by the backend.
     *
     * @param subscription The subscription that has been changed.
     */
    fun onSubscriptionChanged(subscription: ISubscription)
}
