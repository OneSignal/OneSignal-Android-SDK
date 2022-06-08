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

        /**
         * Whether this subscription should be automatically unsubscribed (deleted) if the
         * user disables notifications on the device/app this subscription is for.
         */
        val unsubscribeWhenNotificationsAreDisabled: Boolean
        ) : Subscription(id, enabled) {
}
