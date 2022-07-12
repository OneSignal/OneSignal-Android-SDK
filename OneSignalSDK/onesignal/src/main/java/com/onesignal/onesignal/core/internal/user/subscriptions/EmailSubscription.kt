package com.onesignal.onesignal.core.internal.user.subscriptions

import com.onesignal.onesignal.core.user.subscriptions.IEmailSubscription
import java.util.*

class EmailSubscription(
        id: UUID,
        enabled: Boolean,

        /**
         * The email address notifications will be sent to for this subscription.
         */
        override val email: String
        ) : Subscription(id, enabled), IEmailSubscription
