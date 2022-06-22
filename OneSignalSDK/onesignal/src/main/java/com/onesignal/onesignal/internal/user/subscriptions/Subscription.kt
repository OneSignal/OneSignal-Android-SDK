package com.onesignal.onesignal.internal.user.subscriptions

import com.onesignal.onesignal.user.subscriptions.ISubscription
import java.util.*

/**
 * An abstract subscription represents an open channel between
 * OneSignal and a user.
 */
abstract class Subscription(
        /**
         * The unique identifier for this subscription.
         */
        override val id: UUID,

        /**
         *  Whether this subscription is current enabled. When enabled, the user is able to
         *  receive notifications through this subscription.
         */
        override val enabled: Boolean
) : ISubscription
