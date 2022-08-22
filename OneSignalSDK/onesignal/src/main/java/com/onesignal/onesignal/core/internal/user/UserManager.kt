package com.onesignal.onesignal.core.internal.user

import com.onesignal.onesignal.core.user.IUserManager
import com.onesignal.onesignal.core.user.subscriptions.SubscriptionList
import com.onesignal.onesignal.core.LogLevel
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.models.*

interface IUserSwitcher {
    val identityModel: IdentityModel
    val propertiesModel: PropertiesModel
    fun setUser(identityModel: IdentityModel, propertiesModel: PropertiesModel)
}

internal open class UserManager(
    private val _subscriptionManager: ISubscriptionManager,
    private val _triggerModelStore: TriggerModelStore,
) : IUserManager, IUserSwitcher {

    override val externalId: String?
        get() = identityModel.userId

    override var language: String
        get() = propertiesModel.language
        set(value) { propertiesModel.language = value }

    override val tags: Map<String, String>
        get() = propertiesModel.tags

    override val aliases: Map<String, String>
        get() = identityModel.aliases

    override var subscriptions: SubscriptionList = SubscriptionList(listOf())

    //    private var userModel: UserModel
    override var identityModel: IdentityModel = IdentityModel()
    override var propertiesModel: PropertiesModel = PropertiesModel()

    override fun setUser(identityModel: IdentityModel, propertiesModel: PropertiesModel) {
        this.identityModel = identityModel
        this.propertiesModel = propertiesModel
        _subscriptionManager.load(identityModel)
    }

    override fun addAlias(label: String, id: String) : com.onesignal.onesignal.core.user.IUserManager {
        Logging.log(LogLevel.DEBUG, "setAlias(label: $label, id: $id)")
        val aliases = identityModel.aliases.toMutableMap()
        aliases[label] = id
        identityModel.aliases = aliases
        return this
    }

    override fun addAliases(aliases: Map<String, String>): IUserManager {
        Logging.log(LogLevel.DEBUG, "addAliases(aliases: $aliases")
        val existingAliases = identityModel.aliases.toMutableMap()

        aliases.forEach {
            existingAliases[it.key] = it.value
        }

        identityModel.aliases = aliases
        return this
    }

    override fun removeAlias(label: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "removeAlias(label: $label)")
        val aliases = identityModel.aliases.toMutableMap()
        aliases.remove(label)
        identityModel.aliases = aliases
        return this
    }

    override fun addEmailSubscription(email: String, emailAuthHash: String?): IUserManager {
        Logging.log(LogLevel.DEBUG, "addEmailSubscription(email: $email, emailAuthHash: $emailAuthHash)")
        _subscriptionManager.addEmailSubscription(email, emailAuthHash)
        return this
    }

    override fun removeEmailSubscription(email: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "removeEmailSubscription(email: $email)")
        _subscriptionManager.removeEmailSubscription(email)
        return this
    }

    override fun addSmsSubscription(sms: String, smsAuthHash: String?): IUserManager {
        Logging.log(LogLevel.DEBUG, "addSmsSubscription(sms: $sms, smsAuthHash: $smsAuthHash)")
        _subscriptionManager.addSmsSubscription(sms, smsAuthHash)
        return this
    }

    override fun removeSmsSubscription(sms: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "removeSmsSubscription(sms: $sms)")
        _subscriptionManager.removeSmsSubscription(sms)
        return this
    }

    override fun setTag(key: String, value: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "setTag(key: $key, value: $value)")

        val tags = propertiesModel.tags.toMutableMap()
        tags[key] = value
        propertiesModel.tags = tags
        return this
    }

    override fun setTags(tags: Map<String, String>): IUserManager {
        Logging.log(LogLevel.DEBUG, "setTags(tags: $tags)")

        val tagCollection = propertiesModel.tags.toMutableMap()

        tags.forEach {
            tagCollection[it.key] = it.value
        }

        propertiesModel.tags = tagCollection
        return this
    }

    override fun removeTag(key: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "removeTag(key: $key)")

        val tags = propertiesModel.tags.toMutableMap()
        tags.remove(key)
        propertiesModel.tags = tags
        return this
    }

    override fun removeTags(keys: Collection<String>): IUserManager {
        Logging.log(LogLevel.DEBUG, "removeTags(keys: $keys)")

        val tagCollection = propertiesModel.tags.toMutableMap()

        keys.forEach {
            tagCollection.remove(it)
        }

        propertiesModel.tags = tagCollection
        return this
    }

    override fun setTriggers(triggers: Map<String, Any>): IUserManager {
        Logging.log(LogLevel.DEBUG, "setTriggers(triggers: $triggers)")

        triggers.forEach { setTrigger(it.key, it.value) }

        return this
    }

    override fun setTrigger(key: String, value: Any): IUserManager {
        Logging.log(LogLevel.DEBUG, "setTrigger(key: $key, value: $value)")

        var triggerModel = _triggerModelStore.get(key)
        if(triggerModel != null)
            triggerModel.value = value
        else {
            triggerModel = TriggerModel()
            triggerModel.key = key
            triggerModel.value = value
            _triggerModelStore.add(key, triggerModel)
        }

        return this
    }

    override fun removeTriggers(keys: Collection<String>): IUserManager {
        Logging.log(LogLevel.DEBUG, "removeTriggers(keys: $keys)")

        keys.forEach { removeTrigger(it) }

        return this
    }

    override fun removeTrigger(key: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "removeTrigger(key: $key)")

        _triggerModelStore.remove(key)

        return this
    }

    override fun clearTriggers(): IUserManager {
        Logging.log(LogLevel.DEBUG, "clearTriggers()")
        _triggerModelStore.clear()
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