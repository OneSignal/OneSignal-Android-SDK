package com.onesignal.user.internal

import com.onesignal.common.events.EventProducer
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.internal.subscriptions.SubscriptionType
import com.onesignal.user.subscriptions.IPushSubscription
import com.onesignal.user.subscriptions.IPushSubscriptionObserver
import com.onesignal.user.subscriptions.PushSubscriptionState

internal open class PushSubscription(
    model: SubscriptionModel,
) : Subscription(model), IPushSubscription {
    val changeHandlersNotifier = EventProducer<IPushSubscriptionObserver>()
    var savedState = fetchState()
        private set;

    override val token: String
        get() = model.address

    override val optedIn: Boolean
        get() = model.optedIn && model.status != SubscriptionStatus.NO_PERMISSION

    override fun optIn() {
        // we set `optedIn` using the lower level method so we can set `forceChange=true`, which
        // will result in *always* driving change notification.
        model.setBooleanProperty(SubscriptionModel::optedIn.name, true, forceChange = true)
    }

    override fun optOut() {
        model.optedIn = false
    }

    override fun addObserver(observer: IPushSubscriptionObserver) = changeHandlersNotifier.subscribe(observer)
    override fun removeObserver(observer: IPushSubscriptionObserver) = changeHandlersNotifier.unsubscribe(observer)

    fun refreshState() : PushSubscriptionState {
        savedState = fetchState()
        return savedState
    }

    private fun fetchState() : PushSubscriptionState {
        return PushSubscriptionState(id, token, optedIn)
    }
}

internal class UninitializedPushSubscription() : PushSubscription(createFakePushSub()) {
    companion object {
        fun createFakePushSub(): SubscriptionModel {
            val pushSubModel = SubscriptionModel()
            pushSubModel.id = ""
            pushSubModel.type = SubscriptionType.PUSH
            pushSubModel.optedIn = false
            pushSubModel.address = ""
            return pushSubModel
        }
    }
}
