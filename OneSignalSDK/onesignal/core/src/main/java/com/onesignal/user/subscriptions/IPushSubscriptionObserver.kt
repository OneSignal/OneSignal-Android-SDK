package com.onesignal.user.subscriptions

/**
 * A subscription changed handler. Implement this interface and provide the implementation
 * to [ISubscription.addObserver] to be notified when the subscription has changed.
 */
interface IPushSubscriptionObserver {
    /**
     * Called when the subscription this change handler was added to, has changed. A
     * subscription can change either because of a change driven by the application, or
     * by the backend.
     *
     * @param state The subscription changed state.
     */
    fun onPushSubscriptionChange(state: PushSubscriptionChangedState)
}
