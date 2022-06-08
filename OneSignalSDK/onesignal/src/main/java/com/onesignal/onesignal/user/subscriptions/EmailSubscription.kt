package com.onesignal.onesignal.user.subscriptions

/**
 * An email subscription allows a user to receive notifications through the email
 * channel.
 */
class EmailSubscription(
        id: String,
        enabled: Boolean,

        /**
         * The email address notifications will be sent to for this subscription.
         */
        val email: String
        ) : Subscription(id, enabled) {
}
