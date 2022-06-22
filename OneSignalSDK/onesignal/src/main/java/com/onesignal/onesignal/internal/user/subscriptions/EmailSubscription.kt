package com.onesignal.onesignal.internal.user.subscriptions

import com.onesignal.onesignal.user.subscriptions.IEmailSubscription
import java.util.*

class EmailSubscription(
        id: UUID,
        enabled: Boolean,

        /**
         * The email address notifications will be sent to for this subscription.
         */
        override val email: String
        ) : Subscription(id, enabled), IEmailSubscription
