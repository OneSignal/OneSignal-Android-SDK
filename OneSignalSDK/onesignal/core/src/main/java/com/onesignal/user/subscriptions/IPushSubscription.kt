package com.onesignal.user.subscriptions

/**
 * A push subscription allows a user to receive notifications through the push
 * channel.
 */
interface IPushSubscription : ISubscription {

    /**
     * The token which identifies the device/app that notifications are to be sent. May
     * be an empty string, indicating the push token has not yet been retrieved.
     */
    val token: String

    /**
     *  Whether the user of this subscription is opted-in to received notifications. When true,
     *  the user is able to receive notifications through this subscription. Otherwise, the
     *  user will not receive notifications through this subscription (even when the user has
     *  granted app permission).
     */
    val optedIn: Boolean

    /**
     * Opt the user into this push subscription.  If the application does not have permission,
     * the user will be prompted by Android to permit push permissions.  If the user has
     * granted app permission, the user will be able to receive push notification.  If the user
     * rejects app permission, the user will only be able to receive push notifications once
     * the app permission has been granted.
     */
    fun optIn()

    /**
     * Opt the user out of this push subscription.  The user will no longer received push
     * notifications, although the app permission state will not be changed.
     */
    fun optOut()
}
