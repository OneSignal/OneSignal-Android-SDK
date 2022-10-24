package com.onesignal.user.internal

import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.subscriptions.ISubscription

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
