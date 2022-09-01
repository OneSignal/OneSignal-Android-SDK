package com.onesignal.core.user.subscriptions

import java.util.UUID

/**
 * A subscription
 */
interface ISubscription {
    /**
     * The unique identifier for this subscription.
     */
    val id: UUID
}
