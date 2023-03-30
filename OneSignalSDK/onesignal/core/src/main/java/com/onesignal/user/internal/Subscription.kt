package com.onesignal.user.internal

import com.onesignal.common.IDManager
import com.onesignal.common.events.EventProducer
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.subscriptions.ISubscription
import com.onesignal.user.subscriptions.ISubscriptionChangedHandler

/**
 * An abstract subscription represents an open channel between
 * OneSignal and a user.
 */
internal abstract class Subscription(
    val model: SubscriptionModel,
) : ISubscription {

    val changeHandlersNotifier = EventProducer<ISubscriptionChangedHandler>()

    override val id: String get() = if (IDManager.isLocalId(model.id)) "" else model.id

    override fun addChangeHandler(handler: ISubscriptionChangedHandler) = changeHandlersNotifier.subscribe(handler)
    override fun removeChangeHandler(handler: ISubscriptionChangedHandler) = changeHandlersNotifier.unsubscribe(handler)
}
