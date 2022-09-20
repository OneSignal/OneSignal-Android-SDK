package com.onesignal.core.internal.user.subscriptions

import com.onesignal.core.internal.models.SubscriptionModel
import com.onesignal.core.user.subscriptions.ISubscription

/**
 * An abstract subscription represents an open channel between
 * OneSignal and a user.
 */
internal abstract class Subscription(
    protected val model: SubscriptionModel
) : ISubscription {
    /**
     * The unique identifier for this subscription.
     */
    override val id: String get() = model.id
}
