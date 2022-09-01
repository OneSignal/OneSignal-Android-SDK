package com.onesignal.core.internal.user.subscriptions

import com.onesignal.core.user.subscriptions.ISubscription
import java.util.UUID

/**
 * An abstract subscription represents an open channel between
 * OneSignal and a user.
 */
internal abstract class Subscription(
    /**
     * The unique identifier for this subscription.
     */
    override val id: UUID,
) : ISubscription
