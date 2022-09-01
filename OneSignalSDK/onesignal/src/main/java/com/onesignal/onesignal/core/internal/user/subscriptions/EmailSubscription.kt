package com.onesignal.onesignal.core.internal.user.subscriptions

import com.onesignal.onesignal.core.user.subscriptions.IEmailSubscription
import java.util.UUID

class EmailSubscription(
    id: UUID,

    /**
     * The email address notifications will be sent to for this subscription.
     */
    override val email: String
) : Subscription(id), IEmailSubscription
