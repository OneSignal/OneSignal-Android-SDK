package com.onesignal.onesignal.user.subscriptions

import java.util.*

/**
 * A subscription
 */
interface ISubscription {
        /**
         * The unique identifier for this subscription.
         */
        val id: UUID

        /**
         *  Whether this subscription is current enabled. When enabled, the user is able to
         *  receive notifications through this subscription.
         */
        val enabled: Boolean
}