package com.onesignal.core.internal.user.subscriptions

import com.onesignal.core.user.subscriptions.IEmailSubscription
import java.util.UUID

internal class EmailSubscription(
    id: UUID,

    /**
     * The email address notifications will be sent to for this subscription.
     */
    override val email: String
) : Subscription(id), IEmailSubscription
