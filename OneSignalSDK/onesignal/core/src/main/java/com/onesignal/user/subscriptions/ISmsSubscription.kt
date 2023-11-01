package com.onesignal.user.subscriptions

/**
 * A SMS subscription allows a user to receive notifications through the email
 * channel.
 */
interface ISmsSubscription : ISubscription {
    /**
     * The phone number notifications will be sent to for this subscription, in
     * [E.164](https://documentation.onesignal.com/docs/sms-faq#what-is-the-e164-format) format.
     */
    val number: String
}
