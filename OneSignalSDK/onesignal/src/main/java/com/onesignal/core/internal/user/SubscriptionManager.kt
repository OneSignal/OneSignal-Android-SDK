package com.onesignal.core.internal.user

import com.onesignal.core.debug.LogLevel
import com.onesignal.core.internal.common.events.EventProducer
import com.onesignal.core.internal.common.events.IEventNotifier
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.models.IdentityModel
import com.onesignal.core.internal.models.SubscriptionModel
import com.onesignal.core.internal.models.SubscriptionModelStore
import com.onesignal.core.internal.models.SubscriptionType
import com.onesignal.core.internal.user.subscriptions.EmailSubscription
import com.onesignal.core.internal.user.subscriptions.PushSubscription
import com.onesignal.core.internal.user.subscriptions.SmsSubscription
import com.onesignal.core.user.subscriptions.ISubscription
import com.onesignal.core.user.subscriptions.SubscriptionList
import java.util.UUID

internal interface ISubscriptionManager : IEventNotifier<ISubscriptionChangedHandler> {
    var subscriptions: SubscriptionList

    fun load(identity: IdentityModel)
    fun addEmailSubscription(email: String, emailAuthHash: String?)
    fun addPushSubscription(id: String, pushToken: String)
    fun addSmsSubscription(sms: String, smsAuthHash: String?)
    fun removeEmailSubscription(email: String)
    fun removeSmsSubscription(sms: String)

    fun setSubscriptionEnablement(subscription: ISubscription, enabled: Boolean)
}

internal interface ISubscriptionChangedHandler {
    fun onSubscriptionAdded(subscription: ISubscription)
    fun onSubscriptionRemoved(subscription: ISubscription)
}

internal open class SubscriptionManager(
    private val _subscriptionModelStore: SubscriptionModelStore,
) : ISubscriptionManager {

    private val _events = EventProducer<ISubscriptionChangedHandler>()
    override var subscriptions: SubscriptionList = SubscriptionList(listOf())

    private var identity: IdentityModel = IdentityModel()

    override fun load(identity: IdentityModel) {
        this.identity = identity

        var subs = mutableListOf<ISubscription>()

        for (s in _subscriptionModelStore.list()) {
            // TODO: Better way to find subscriptions for a user?
            if (s.startsWith(identity.oneSignalId.toString())) {
                val model = _subscriptionModelStore.get(s)

                when (model?.type) {
                    SubscriptionType.EMAIL -> {
                        subs.add(EmailSubscription(UUID.fromString(model.id), model.address))
                    }
                    SubscriptionType.SMS -> {
                        subs.add(SmsSubscription(UUID.fromString(model.id), model.address))
                    }
                    SubscriptionType.PUSH -> {
                        // TODO: Determine if is this device, set bool appropriately
                        subs.add(PushSubscription(UUID.fromString(model.id), model.enabled, model.address, this))
                    }
                }
            }
        }

        this.subscriptions = SubscriptionList(subs)
    }
    override fun addEmailSubscription(email: String, emailAuthHash: String?) {
        var emailSub = EmailSubscription(UUID.randomUUID(), email)
        val subscriptions = subscriptions.collection.toMutableList()
        subscriptions.add(emailSub)
        this.subscriptions = SubscriptionList(subscriptions)

        var emailSubModel = SubscriptionModel()
        emailSubModel.id = emailSub.id.toString()
        emailSubModel.enabled = true
        emailSubModel.type = SubscriptionType.EMAIL
        emailSubModel.address = emailSub.email
        _subscriptionModelStore.add(identity.oneSignalId.toString() + "-" + emailSub.id, emailSubModel)

        _events.fire { it.onSubscriptionAdded(emailSub) }
    }

    override fun removeEmailSubscription(email: String) {
        val subscriptionToRem = subscriptions.emails.firstOrNull { it is EmailSubscription && it.email == email }

        if (subscriptionToRem != null) {
            removeSubscription(subscriptionToRem)
        }
    }

    override fun addSmsSubscription(sms: String, smsAuthHash: String?) {
        var smsSub = SmsSubscription(UUID.randomUUID(), sms)
        val subscriptions = subscriptions.collection.toMutableList()
        subscriptions.add(smsSub)
        this.subscriptions = SubscriptionList(subscriptions)

        var smsSubModel = SubscriptionModel()
        smsSubModel.id = smsSub.id.toString()
        smsSubModel.enabled = true
        smsSubModel.type = SubscriptionType.SMS
        smsSubModel.address = smsSub.number
        _subscriptionModelStore.add(identity.oneSignalId.toString() + "-" + smsSub.id, smsSubModel)

        _events.fire { it.onSubscriptionAdded(smsSub) }
    }

    override fun removeSmsSubscription(sms: String) {
        val subscriptionToRem = subscriptions.smss.firstOrNull { it.number == sms }

        if (subscriptionToRem != null) {
            removeSubscription(subscriptionToRem)
        }
    }

    override fun addPushSubscription(id: String, pushToken: String) {
        var pushSub = PushSubscription(UUID.fromString(id), true, pushToken, this)

        val subscriptions = subscriptions.collection.toMutableList()
        subscriptions.add(pushSub)
        this.subscriptions = SubscriptionList(subscriptions)

        var pushSubModel = SubscriptionModel()
        pushSubModel.id = pushSub.id.toString()
        pushSubModel.enabled = pushSub.enabled
        pushSubModel.type = SubscriptionType.PUSH
        pushSubModel.address = pushSub.pushToken
        _subscriptionModelStore.add(identity.oneSignalId.toString() + "-" + pushSub.id, pushSubModel, false)

        _events.fire { it.onSubscriptionAdded(pushSub) }
    }

    override fun setSubscriptionEnablement(subscription: ISubscription, enabled: Boolean) {
        Logging.log(LogLevel.DEBUG, "setSubscriptionEnablement(subscription: $subscription, enabled: $enabled)")

        val subscriptionModel = _subscriptionModelStore.get(identity.oneSignalId.toString() + "-" + subscription.id)

        if (subscriptionModel != null) {
            subscriptionModel.enabled = enabled
        }

        // remove the old subscription and add a new one with the proper enablement
        val subscriptions = subscriptions.collection.toMutableList()
        subscriptions.remove(subscription)
        when (subscription) {
            is SmsSubscription -> {
                subscriptions.add(SmsSubscription(subscription.id, subscription.number))
            }
            is EmailSubscription -> {
                subscriptions.add(EmailSubscription(subscription.id, subscription.email))
            }
            is PushSubscription -> {
                subscriptions.add(PushSubscription(subscription.id, subscription.enabled, subscription.pushToken, this))
            }
        }
        this.subscriptions = SubscriptionList(subscriptions)
    }

    override fun subscribe(handler: ISubscriptionChangedHandler) = _events.subscribe(handler)

    override fun unsubscribe(handler: ISubscriptionChangedHandler) = _events.unsubscribe(handler)

    private fun removeSubscription(subscription: ISubscription) {
        Logging.log(LogLevel.DEBUG, "removeSubscription(subscription: $subscription)")

        val subscriptions = subscriptions.collection.toMutableList()
        subscriptions.remove(subscription)
        this.subscriptions = SubscriptionList(subscriptions)

        _subscriptionModelStore.remove(identity.oneSignalId.toString() + "-" + subscription.id)

        _events.fire { it.onSubscriptionRemoved(subscription) }
    }
}
