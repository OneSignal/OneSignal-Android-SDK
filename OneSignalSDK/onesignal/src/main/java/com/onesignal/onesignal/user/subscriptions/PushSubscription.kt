package com.onesignal.onesignal.user.subscriptions

/**
 * A push subscription allows a user to receive notifications through the push
 * channel.
 */
class PushSubscription(
        id: String,
        enabled: Boolean,

        /**
         * The token which identifies the device/app that notifications are to be sent.
         */
        val pushToken: String,

        ) : Subscription(id, enabled) {
}
