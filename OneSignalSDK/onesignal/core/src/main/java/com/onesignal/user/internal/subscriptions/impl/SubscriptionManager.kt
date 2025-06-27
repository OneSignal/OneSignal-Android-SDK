package com.onesignal.user.internal.subscriptions.impl

import android.os.Build
import android.util.Log
import com.onesignal.common.AndroidUtils
import com.onesignal.common.DeviceUtils
import com.onesignal.common.IDManager
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.events.EventProducer
import com.onesignal.common.modeling.IModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.session.ISessionLifecycleHandler
import com.onesignal.session.internal.session.ISessionService
import com.onesignal.user.internal.EmailSubscription
import com.onesignal.user.internal.PushSubscription
import com.onesignal.user.internal.SmsSubscription
import com.onesignal.user.internal.Subscription
import com.onesignal.user.internal.UninitializedPushSubscription
import com.onesignal.user.internal.subscriptions.ISubscriptionChangedHandler
import com.onesignal.user.internal.subscriptions.ISubscriptionManager
import com.onesignal.user.internal.subscriptions.SubscriptionList
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.internal.subscriptions.SubscriptionType
import com.onesignal.user.subscriptions.ISubscription
import com.onesignal.user.subscriptions.PushSubscriptionChangedState

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
    private val _applicationService: IApplicationService,
    private val _sessionService: ISessionService,
    private val _subscriptionModelStore: SubscriptionModelStore,
) : ISubscriptionManager, IModelStoreChangeHandler<SubscriptionModel>, ISessionLifecycleHandler {
    private val events = EventProducer<ISubscriptionChangedHandler>()
    override var subscriptions: SubscriptionList = SubscriptionList(listOf(), UninitializedPushSubscription())
    override val pushSubscriptionModel: SubscriptionModel
        get() = (subscriptions.push as PushSubscription).model

    init {
        for (subscriptionModel in _subscriptionModelStore.list()) {
            createSubscriptionAndAddToSubscriptionList(subscriptionModel)
        }

        _subscriptionModelStore.subscribe(this)
        _sessionService.subscribe(this)
    }

    override fun onSessionStarted() {
        refreshPushSubscriptionState()
    }

    override fun onSessionActive() { }

    override fun onSessionEnded(duration: Long) { }

    override fun addEmailSubscription(email: String) {
        addSubscriptionToModels(SubscriptionType.EMAIL, email)
    }

    override fun addSmsSubscription(sms: String) {
        addSubscriptionToModels(SubscriptionType.SMS, sms)
    }

    override fun addOrUpdatePushSubscriptionToken(
        pushToken: String?,
        pushTokenStatus: SubscriptionStatus,
    ) {
        val pushSub = subscriptions.push

        if (pushSub is UninitializedPushSubscription) {
            addSubscriptionToModels(SubscriptionType.PUSH, pushToken ?: "", pushTokenStatus)
        } else {
            val pushSubModel = (pushSub as Subscription).model

            if (pushToken != null) {
                pushSubModel.address = pushToken
            }

            pushSubModel.status = pushTokenStatus
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

    private fun addSubscriptionToModels(
        type: SubscriptionType,
        address: String,
        status: SubscriptionStatus? = null,
    ) {
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

    override fun subscribe(handler: ISubscriptionChangedHandler) = events.subscribe(handler)

    override fun unsubscribe(handler: ISubscriptionChangedHandler) = events.unsubscribe(handler)

    override val hasSubscribers: Boolean
        get() = events.hasSubscribers

    /**
     * Called when the model store has added a new subscription. The subscription list must be updated
     * to reflect the added subscription.
     */
    override fun onModelAdded(
        model: SubscriptionModel,
        tag: String,
    ) {
        createSubscriptionAndAddToSubscriptionList(model)
    }

    /**
     * Called when a subscription model has been updated. The subscription list must be updated
     * to reflect the update subscription.
     */
    override fun onModelUpdated(
        args: ModelChangedArgs,
        tag: String,
    ) {
        Log.d("app", "❌ SubscriptionManager.onModelUpdated TOP")

        val subscription =
            subscriptions.collection.firstOrNull {
                args.model == (it as Subscription).model
            }

        if (subscription == null) {
            // this shouldn't happen, but create a new subscription if a model was updated and we
            // don't yet have a representation for it in the subscription list.
            Log.d("app", "❌ onModelUpdated called for PushSubscription - NULL!!!")

            createSubscriptionAndAddToSubscriptionList(args.model as SubscriptionModel)
        } else {
            if (subscription is PushSubscription) {
                Log.d("app", "❌ onModelUpdated called for PushSubscription")
                subscription.changeHandlersNotifier.fireOnMain {
                    it.onPushSubscriptionChange(
                        PushSubscriptionChangedState(
                            subscription.savedState,
                            subscription.refreshState(),
                        ),
                    )
                }
            }
            // the model has already been updated, so fire the update event
            events.fire { it.onSubscriptionChanged(subscription, args) }
        }
    }

    /**
     * Called when a subscription model has been removed. The subscription list must be updated
     * to reflect the subscription removed.
     */
    override fun onModelRemoved(
        model: SubscriptionModel,
        tag: String,
    ) {
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
            val existingPushSubscription = this.subscriptions.push as PushSubscription
            ((subscription as PushSubscription).changeHandlersNotifier).subscribeAll(existingPushSubscription.changeHandlersNotifier)
            subscriptions.remove(existingPushSubscription)
        }

        subscriptions.add(subscription)
        this.subscriptions = SubscriptionList(subscriptions, UninitializedPushSubscription())

        events.fire { it.onSubscriptionAdded(subscription) }
    }

    private fun removeSubscriptionFromSubscriptionList(subscription: ISubscription) {
        val subscriptions = subscriptions.collection.toMutableList()
        subscriptions.remove(subscription)
        this.subscriptions = SubscriptionList(subscriptions, UninitializedPushSubscription())

        events.fire { it.onSubscriptionRemoved(subscription) }
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

    /**
     * Called when app has gained focus and the subscription state should be refreshed.
     */
    private fun refreshPushSubscriptionState() {
        val pushSub = subscriptions.push

        if (pushSub is UninitializedPushSubscription) {
            return
        }
        val pushSubModel = (pushSub as Subscription).model

        pushSubModel.sdk = OneSignalUtils.SDK_VERSION
        pushSubModel.deviceOS = Build.VERSION.RELEASE

        val carrier = DeviceUtils.getCarrierName(_applicationService.appContext)
        carrier?.let {
            pushSubModel.carrier = carrier
        }

        val appVersion = AndroidUtils.getAppVersion(_applicationService.appContext)
        appVersion?.let {
            pushSubModel.appVersion = appVersion
        }
    }
}
