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
    val pushToken: String

    /**
     *  Whether this subscription is current enabled. When enabled, the user is able to
     *  receive notifications through this subscription.
     */
    var enabled: Boolean
}
