package com.onesignal.onesignal.core.internal.user

import com.onesignal.onesignal.core.internal.modeling.IModelStore
import com.onesignal.onesignal.core.internal.models.IdentityModel
import com.onesignal.onesignal.core.internal.models.PropertiesModel
import com.onesignal.onesignal.core.internal.models.SubscriptionModel
import com.onesignal.onesignal.core.internal.models.SubscriptionType
import com.onesignal.onesignal.core.internal.user.subscriptions.EmailSubscription
import com.onesignal.onesignal.core.internal.user.subscriptions.PushSubscription
import com.onesignal.onesignal.core.internal.user.subscriptions.SmsSubscription
import com.onesignal.onesignal.core.internal.user.triggers.Trigger
import com.onesignal.onesignal.core.user.IUserManager
import com.onesignal.onesignal.core.user.Identity
import com.onesignal.onesignal.core.user.subscriptions.ISubscription
import com.onesignal.onesignal.core.user.subscriptions.SubscriptionList
import com.onesignal.onesignal.core.internal.logging.LogLevel
import com.onesignal.onesignal.core.internal.logging.Logging
import java.util.*

interface IUserSwitcher {
    fun setUser(identityModel: IdentityModel, propertiesModel: PropertiesModel)
}

open class UserManager(
    private val _subscriptionModelStore: IModelStore<SubscriptionModel>
) : IUserManager, IUserSwitcher {
    override val identity: Identity
        get() {
            val userId = _identityModel.userId
            return if (userId.isNullOrBlank())
                Identity.Anonymous()     // TODO: should probably construct once and keep around
            else
                Identity.Known(userId!!) // TODO: should probably construct once and keep around
        }

    override var language: String
        get() = _propertiesModel.language
        set(value) { _propertiesModel.language = value }

    override val tags: Map<String, String>
        get() = _propertiesModel.tags

    override val aliases: Map<String, String>
        get() = _identityModel.aliases

    // NOT IN USER MODEL
    override var privacyConsent: Boolean = false
    override var subscriptions: SubscriptionList = SubscriptionList(listOf())

    //    private var userModel: UserModel
    private val _triggers: List<Trigger> = listOf()
    private var _identityModel: IdentityModel = IdentityModel()
    private var _propertiesModel: PropertiesModel = PropertiesModel()

    override fun setUser(identityModel: IdentityModel, propertiesModel: PropertiesModel) {
        _identityModel = identityModel
        _propertiesModel = propertiesModel

        var subs = mutableListOf<ISubscription>()

        for(s in _subscriptionModelStore.list()) {
            // TODO: Better way to find subscriptions for a user?
            if(s.startsWith(_identityModel.oneSignalId.toString()))
            {
                val model = _subscriptionModelStore.get(s);

                when (model?.type) {
                    SubscriptionType.EMAIL -> {
                        subs.add(EmailSubscription(UUID.fromString(model.id), model.enabled, model.address))
                    }
                    SubscriptionType.SMS -> {
                        subs.add(SmsSubscription(UUID.fromString(model.id), model.enabled, model.address))
                    }
                    SubscriptionType.PUSH -> {
                        // TODO: Determine if is this device, set bool appropriately
                        subs.add(PushSubscription(UUID.fromString(model.id), model.enabled, model.address, true))
                    }
                }
            }
        }

        this.subscriptions = SubscriptionList(subs)
    }

    override fun setExternalId(externalId: String?): IUserManager {
        Logging.log(LogLevel.DEBUG, "setExternalId(externalId: $externalId)")

        _identityModel.userId = externalId
        _identityModel.userIdAuthHash = null
        return this
    }

    override fun setExternalId(externalId: String?, externalIdAuthHash: String?): IUserManager {
        Logging.log(LogLevel.DEBUG, "setExternalId(externalId: $externalId, externalIdAuthHash: $externalIdAuthHash)")

        _identityModel.userId = externalId
        _identityModel.userIdAuthHash = externalIdAuthHash
        return this
    }

    override fun setAlias(label: String, id: String) : com.onesignal.onesignal.core.user.IUserManager {
        Logging.log(LogLevel.DEBUG, "setAlias(label: $label, id: $id)")
        val aliases = _identityModel.aliases.toMutableMap()
        aliases[label] = id
        _identityModel.aliases = aliases
        return this
    }

    override fun removeAlias(label: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "removeAlias(label: $label)")
        val aliases = _identityModel.aliases.toMutableMap()
        aliases.remove(label)
        _identityModel.aliases = aliases
        return this
    }

    override fun addEmailSubscription(email: String, emailAuthHash: String?): IUserManager {
        Logging.log(LogLevel.DEBUG, "addEmailSubscription(email: $email, emailAuthHash: $emailAuthHash)")

        var emailSub = EmailSubscription(UUID.randomUUID(), true, email)
        val subscriptions = subscriptions.collection.toMutableList()
        subscriptions.add(emailSub)
        this.subscriptions = SubscriptionList(subscriptions)

        var emailSubModel = SubscriptionModel()
        emailSubModel.id = emailSub.id.toString()
        emailSubModel.enabled = emailSub.enabled
        emailSubModel.type = SubscriptionType.EMAIL
        emailSubModel.address = emailSub.email
        _subscriptionModelStore.add(_identityModel.oneSignalId.toString() + "-" + emailSub.id, emailSubModel)

        return this
    }

    override fun addSmsSubscription(sms: String, smsAuthHash: String?): IUserManager {
        Logging.log(LogLevel.DEBUG, "addSmsSubscription(sms: $sms, smsAuthHash: $smsAuthHash)")

        var smsSub = SmsSubscription(UUID.randomUUID(), true, sms)
        val subscriptions = subscriptions.collection.toMutableList()
        subscriptions.add(smsSub)
        this.subscriptions = SubscriptionList(subscriptions)

        var smsSubModel = SubscriptionModel()
        smsSubModel.id = smsSub.id.toString()
        smsSubModel.enabled = smsSub.enabled
        smsSubModel.type = SubscriptionType.SMS
        smsSubModel.address = smsSub.number
        _subscriptionModelStore.add(_identityModel.oneSignalId.toString() + "-" + smsSub.id, smsSubModel)

        return this
    }

    override fun addPushSubscription(): IUserManager {
        Logging.log(LogLevel.DEBUG, "addPushSubscription()")

        // TODO: Actually register this device for push
        var pushSub = PushSubscription(UUID.randomUUID(), true, UUID.randomUUID().toString(), true)

        val subscriptions = subscriptions.collection.toMutableList()
        subscriptions.add(pushSub)
        this.subscriptions = SubscriptionList(subscriptions)

        var pushSubModel = SubscriptionModel()
        pushSubModel.id = pushSub.id.toString()
        pushSubModel.enabled = pushSub.enabled
        pushSubModel.type = SubscriptionType.PUSH
        pushSubModel.address = pushSub.pushToken
        _subscriptionModelStore.add(_identityModel.oneSignalId.toString() + "-" + pushSub.id, pushSubModel)

        return this
    }

    override fun setSubscriptionEnablement(subscription: ISubscription, enabled: Boolean): IUserManager {
        Logging.log(LogLevel.DEBUG, "setSubscriptionEnablement(subscription: $subscription, enabled: $enabled)")

        val subscriptionModel = _subscriptionModelStore.get(_identityModel.oneSignalId.toString() + "-" + subscription.id)

        if(subscriptionModel != null) {
            subscriptionModel.enabled = enabled
        }

        // remove the old subscription and add a new one with the proper enablement
        val subscriptions = subscriptions.collection.toMutableList()
        subscriptions.remove(subscription)
        when (subscription) {
            is SmsSubscription -> {
                subscriptions.add(SmsSubscription(subscription.id, subscription.enabled, subscription.number))
            }
            is EmailSubscription -> {
                subscriptions.add(EmailSubscription(subscription.id, subscription.enabled, subscription.email))
            }
            is PushSubscription -> {
                subscriptions.add(PushSubscription(subscription.id, subscription.enabled, subscription.pushToken, subscription.isThisDevice))
            }
        }
        this.subscriptions = SubscriptionList(subscriptions)

        return this
    }

    override fun removeSubscription(subscription: ISubscription): IUserManager {
        Logging.log(LogLevel.DEBUG, "removeSubscription(subscription: $subscription)")

        val subscriptions = subscriptions.collection.toMutableList()
        subscriptions.remove(subscription)
        this.subscriptions = SubscriptionList(subscriptions)

        _subscriptionModelStore.remove(_identityModel.oneSignalId.toString() + "-" + subscription.id)

        return this
    }

    override fun setTag(key: String, value: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "setTag(key: $key, value: $value)")

        val tags = _propertiesModel.tags.toMutableMap()
        tags[key] = value
        _propertiesModel.tags = tags
        return this
    }

    override fun setTags(tags: Map<String, String>): IUserManager {
        Logging.log(LogLevel.DEBUG, "setTags(tags: $tags)")

        val tagCollection = _propertiesModel.tags.toMutableMap()

        tags.forEach {
            tagCollection[it.key] = it.value
        }

        _propertiesModel.tags = tagCollection
        return this
    }

    override fun removeTag(key: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "removeTag(key: $key)")

        val tags = _propertiesModel.tags.toMutableMap()
        tags.remove(key)
        _propertiesModel.tags = tags
        return this
    }

    override fun removeTags(keys: Collection<String>): IUserManager {
        Logging.log(LogLevel.DEBUG, "removeTags(keys: $keys)")

        val tagCollection = _propertiesModel.tags.toMutableMap()

        keys.forEach {
            tagCollection.remove(it)
        }

        _propertiesModel.tags = tagCollection
        return this
    }

    override fun setTriggers(triggers: Map<String, Any>): IUserManager {
        Logging.log(LogLevel.DEBUG, "setTriggers(triggers: $triggers)")
        //TODO("Not yet implemented")
        return this
    }

    override fun setTrigger(key: String, value: Any): IUserManager {
        Logging.log(LogLevel.DEBUG, "setTrigger(key: $key, value: $value)")
        //TODO("Not yet implemented")
        return this
    }

    override fun removeTriggers(keys: Collection<String>): IUserManager {
        Logging.log(LogLevel.DEBUG, "removeTriggers(keys: $keys)")
        //TODO("Not yet implemented")
        return this
    }

    override fun removeTrigger(key: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "removeTrigger(key: $key)")
        return this
    }

    override fun clearTriggers(): IUserManager {
        Logging.log(LogLevel.DEBUG, "clearTriggers()")
        //TODO("Not yet implemented")
        return this
    }

    override fun sendOutcome(name: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "sendOutcome(name: $name)")
        //TODO("Not yet implemented")
        return this
    }

    override fun sendUniqueOutcome(name: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "sendUniqueOutcome(name: $name)")
        //TODO("Not yet implemented")
        return this
    }

    override fun sendOutcomeWithValue(name: String, value: Float): IUserManager {
        Logging.log(LogLevel.DEBUG, "sendOutcomeWithValue(name: $name, value: $value)")
        //TODO("Not yet implemented")
        return this
    }
}