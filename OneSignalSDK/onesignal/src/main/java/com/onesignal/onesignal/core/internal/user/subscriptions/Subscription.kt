package com.onesignal.onesignal.core.internal.user.subscriptions

import com.onesignal.onesignal.core.user.subscriptions.ISubscription
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
) : ISubscription
