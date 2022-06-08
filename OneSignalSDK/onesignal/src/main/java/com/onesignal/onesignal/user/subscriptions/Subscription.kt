package com.onesignal.onesignal.user.subscriptions

/**
 * An abstract subscription represents an open channel between
 * OneSignal and a user.
 */
abstract class Subscription(
        /**
         * The unique identifier for this subscription.
         */
        val id: String,

        /**
         *  Whether this subscription is current enabled. When enabled, the user is able to
         *  receive notifications through this subscription.
         */
        val enabled: Boolean
) {
}
