package com.onesignal.user.internal.subscriptions.impl

import com.onesignal.common.IDManager
import com.onesignal.common.events.EventProducer
import com.onesignal.common.modeling.IModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.EmailSubscription
import com.onesignal.user.internal.PushSubscription
import com.onesignal.user.internal.SmsSubscription
import com.onesignal.user.internal.Subscription
import com.onesignal.user.internal.UninitializedPushSubscription
import com.onesignal.user.internal.subscriptions.ISubscriptionChangedHandler
import com.onesignal.user.internal.subscriptions.ISubscriptionManager
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.internal.subscriptions.SubscriptionType
import com.onesignal.user.subscriptions.ISubscription
import com.onesignal.user.subscriptions.SubscriptionList

/**
 * The subscription manager is responsible for managing the external representation of the
 * user's subscriptions.  It handles the addition/removal/update of the external representations,
 * as well as responding to internal subscription model changes.
 *
 * In general the subscription manager relies on and reacts to the subscription model store.  Adding
 * a subscription for example will add the subscription to the model store, then utilizing the
 * [IModelStoreChangeHandler.onModelAdded] callback to refresh the appropriate subscription from the
 * subscription model.
 */
internal class SubscriptionManager(
    private val _subscriptionModelStore: SubscriptionModelStore
) : ISubscriptionManager, IModelStoreChangeHandler<SubscriptionModel> {

    private val _events = EventProducer<ISubscriptionChangedHandler>()
    override var subscriptions: SubscriptionList = SubscriptionList(listOf(), UninitializedPushSubscription())
    override val pushSubscriptionModel: SubscriptionModel
        get() = (subscriptions.push as PushSubscription).model

    init {
        for (subscriptionModel in _subscriptionModelStore.list()) {
            createSubscriptionAndAddToSubscriptionList(subscriptionModel)
        }

        _subscriptionModelStore.subscribe(this)
    }

    override fun addEmailSubscription(email: String) {
        addSubscriptionToModels(SubscriptionType.EMAIL, email)
    }

    override fun addSmsSubscription(sms: String) {
        addSubscriptionToModels(SubscriptionType.SMS, sms)
    }

    override fun addOrUpdatePushSubscription(pushToken: String?, pushTokenStatus: SubscriptionStatus) {
        val pushSub = subscriptions.push

        if (pushSub is UninitializedPushSubscription) {
            addSubscriptionToModels(SubscriptionType.PUSH, pushToken ?: "", pushTokenStatus)
        } else {
            updateSubscriptionModel(pushSub) {
                if (pushToken != null) {
                    it.address = pushToken
                }
                it.status = pushTokenStatus
            }
        }
    }

    override fun removeEmailSubscription(email: String) {
        val subscriptionToRem = subscriptions.emails.firstOrNull { it is EmailSubscription && it.email == email }

        if (subscriptionToRem != null) {
            removeSubscriptionFromModels(subscriptionToRem)
        }
    }

    override fun removeSmsSubscription(sms: String) {
        val subscriptionToRem = subscriptions.smss.firstOrNull { it is SmsSubscription && it.number == sms }

        if (subscriptionToRem != null) {
            removeSubscriptionFromModels(subscriptionToRem)
        }
    }

    private fun addSubscriptionToModels(type: SubscriptionType, address: String, status: SubscriptionStatus? = null) {
        Logging.log(LogLevel.DEBUG, "SubscriptionManager.addSubscription(type: $type, address: $address)")

        val subscriptionModel = SubscriptionModel()
        subscriptionModel.id = IDManager.createLocalId()
        subscriptionModel.optedIn = true
        subscriptionModel.type = type
        subscriptionModel.address = address
        subscriptionModel.status = status ?: SubscriptionStatus.SUBSCRIBED

        _subscriptionModelStore.add(subscriptionModel)
    }

    private fun removeSubscriptionFromModels(subscription: ISubscription) {
        Logging.log(LogLevel.DEBUG, "SubscriptionManager.removeSubscription(subscription: $subscription)")

        _subscriptionModelStore.remove(subscription.id)
    }

    private fun updateSubscriptionModel(subscription: ISubscription, update: (SubscriptionModel) -> Unit) {
        val subscriptionModel = _subscriptionModelStore.get(subscription.id) ?: return
        update(subscriptionModel)
    }

    override fun subscribe(handler: ISubscriptionChangedHandler) = _events.subscribe(handler)

    override fun unsubscribe(handler: ISubscriptionChangedHandler) = _events.unsubscribe(handler)

    /**
     * Called when the model store has added a new subscription. The subscription list must be updated
     * to reflect the added subscription.
     */
    override fun onModelAdded(model: SubscriptionModel, tag: String) {
        createSubscriptionAndAddToSubscriptionList(model)
    }

    /**
     * Called when a subscription model has been updated. The subscription list must be updated
     * to reflect the update subscription.
     */
    override fun onModelUpdated(args: ModelChangedArgs, tag: String) {
        val subscription = subscriptions.collection.firstOrNull { it.id == args.model.id }

        if (subscription == null) {
            // this shouldn't happen, but create a new subscription if a model was updated and we
            // don't yet have a representation for it in the subscription list.
            createSubscriptionAndAddToSubscriptionList(args.model as SubscriptionModel)
        } else {
            (subscription as Subscription).changeHandlersNotifier.fireOnMain {
                it.onSubscriptionChanged(
                    subscription
                )
            }

            // the model has already been updated, so fire the update event
            _events.fire { it.onSubscriptionsChanged(subscription, args) }
        }
    }

    /**
     * Called when a subscription model has been removed. The subscription list must be updated
     * to reflect the subscription removed.
     */
    override fun onModelRemoved(model: SubscriptionModel, tag: String) {
        val subscription = subscriptions.collection.firstOrNull { it.id == model.id }

        if (subscription != null) {
            removeSubscriptionFromSubscriptionList(subscription)
        }
    }

    private fun createSubscriptionAndAddToSubscriptionList(subscriptionModel: SubscriptionModel) {
        val subscription = createSubscriptionFromModel(subscriptionModel)

        val subscriptions = this.subscriptions.collection.toMutableList()

        // There can only be 1 push subscription, always transfer any subscribers from the old one to the new
        if (subscriptionModel.type == SubscriptionType.PUSH) {
            val existingPushSubscription = this.subscriptions.push as Subscription
            ((subscription as PushSubscription).changeHandlersNotifier).subscribeAll(existingPushSubscription.changeHandlersNotifier)
            subscriptions.remove(existingPushSubscription)
        }

        subscriptions.add(subscription)
        this.subscriptions = SubscriptionList(subscriptions, UninitializedPushSubscription())

        _events.fire { it.onSubscriptionsAdded(subscription) }
    }

    private fun removeSubscriptionFromSubscriptionList(subscription: ISubscription) {
        val subscriptions = subscriptions.collection.toMutableList()
        subscriptions.remove(subscription)
        this.subscriptions = SubscriptionList(subscriptions, UninitializedPushSubscription())

        _events.fire { it.onSubscriptionRemoved(subscription) }
    }

    private fun createSubscriptionFromModel(subscriptionModel: SubscriptionModel): ISubscription {
        return when (subscriptionModel.type) {
            SubscriptionType.SMS -> {
                SmsSubscription(subscriptionModel)
            }
            SubscriptionType.EMAIL -> {
                EmailSubscription(subscriptionModel)
            }
            SubscriptionType.PUSH -> {
                PushSubscription(subscriptionModel)
            }
        }
    }
}
