package com.onesignal.onesignal.internal.user

import com.onesignal.onesignal.internal.models.IdentityModel
import com.onesignal.onesignal.internal.models.PropertiesModel
import com.onesignal.onesignal.internal.models.SubscriptionModel
import com.onesignal.onesignal.user.IUserManager
import com.onesignal.onesignal.user.Identity
import com.onesignal.onesignal.user.subscriptions.Subscription
import com.onesignal.onesignal.user.subscriptions.SubscriptionList
import com.onesignal.onesignal.internal.user.triggers.TriggerCollection
import com.onesignal.onesignal.logging.LogLevel
import com.onesignal.onesignal.logging.Logging

open class UserManager() : IUserManager {
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

    override var privacyConsent: Boolean = false
    override val subscriptions: SubscriptionList = SubscriptionList(listOf())
    override val tags: Map<String, String> = mapOf()
    override val aliases: Map<String, String> = mapOf()
    val triggers: TriggerCollection = TriggerCollection(listOf())

    //    private var userModel: UserModel
    private var _identityModel: IdentityModel = IdentityModel()
    private var _propertiesModel: PropertiesModel = PropertiesModel()
    private var _subscriptionModels: List<SubscriptionModel> = listOf()

    init {
        // TODO("Populate based on initial model or would that call setUser once loaded?")
        // Following  priority order is used until we have an instance
        //   1. _user - Already set internal instance
        //   2. From local storage - restore user from last time the app opened
        //   3. Create new - Create a brand new UserAnonymous
        // TODO: This should check local storage to see if there was a user assign before
        //  val user = _user ?: UserAnonymous()
    }

    fun setUser(identityModel: IdentityModel, propertiesModel: PropertiesModel) {
        _identityModel = identityModel
        _propertiesModel = propertiesModel

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

    override fun setAlias(label: String, id: String) : com.onesignal.onesignal.user.IUserManager {
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
        //TODO("How do new models get created? Need a hook to the repo to add a new subscription model.")
        return this
    }

    override fun addSmsSubscription(sms: String, smsAuthHash: String?): IUserManager {
        Logging.log(LogLevel.DEBUG, "addSmsSubscription(sms: $sms, smsAuthHash: $smsAuthHash)")
        //TODO("Not yet implemented")
        return this
    }

    override fun setSubscriptionEnablement(subscription: Subscription, enabled: Boolean): IUserManager {
        Logging.log(LogLevel.DEBUG, "setSubscriptionEnablement(subscription: $subscription, enabled: $enabled)")
        //TODO("Not yet implemented")
        return this
    }

    override fun removeSubscription(subscription: Subscription): IUserManager {
        Logging.log(LogLevel.DEBUG, "removeSubscription(subscription: $subscription)")
        //TODO("Not yet implemented")
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