package com.onesignal.user.subscriptions

/**
 * A subscription state.
 */
class PushSubscriptionState(
    /**
     * The unique identifier for this subscription. This will be an empty string
     * until the subscription has been successfully created on the backend and
     * assigned an ID.  Use [addObserver] to be notified when the [id] has
     * been successfully assigned.
     */
    val id: String,

    /**
     * The token which identifies the device/app that notifications are to be sent. May
     * be an empty string, indicating the push token has not yet been retrieved.
     */
    val token: String,

    /**
     *  Whether the user of this subscription is opted-in to received notifications. When true,
     *  the user is able to receive notifications through this subscription. Otherwise, the
     *  user will not receive notifications through this subscription (even when the user has
     *  granted app permission).
     */
    val optedIn: Boolean
)
