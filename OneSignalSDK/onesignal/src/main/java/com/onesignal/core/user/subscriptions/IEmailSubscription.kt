package com.onesignal.core.user.subscriptions

/**
 * An email subscription allows a user to receive notifications through the email
 * channel.
 */
interface IEmailSubscription : ISubscription {
    /**
     * The email address notifications will be sent to for this subscription.
     */
    val email: String
}
