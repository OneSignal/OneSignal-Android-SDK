package com.onesignal.onesignal.core.user.subscriptions

/**
 * A push subscription allows a user to receive notifications through the push
 * channel.
 */
interface IPushSubscription : ISubscription {

    /**
     * The token which identifies the device/app that notifications are to be sent.
     */
    val pushToken: String

    /**
     * When true, this subscription is for this device.
     */
    val isThisDevice: Boolean
}