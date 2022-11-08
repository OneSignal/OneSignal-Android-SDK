package com.onesignal.user.internal

import com.onesignal.core.internal.language.ILanguageContext
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.IUserManager
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.properties.PropertiesModel
import com.onesignal.user.internal.properties.PropertiesModelStore
import com.onesignal.user.internal.subscriptions.ISubscriptionManager
import com.onesignal.user.subscriptions.SubscriptionList

internal open class UserManager(
    private val _subscriptionManager: ISubscriptionManager,
    private val _identityModelStore: IdentityModelStore,
    private val _propertiesModelStore: PropertiesModelStore,
    private val _languageContext: ILanguageContext
) : IUserManager {

    override val externalId: String?
        get() = _identityModel.externalId

    override var language: String
        get() = _languageContext.language
        set(value) { _languageContext.language = value }

    override val tags: Map<String, String>
        get() = _propertiesModel.tags

    override val aliases: Map<String, String>
        get() = _identityModel.filter { it.key != IdentityModel::id.name }.toMap()

    override val subscriptions: SubscriptionList
        get() = _subscriptionManager.subscriptions

    private val _identityModel: IdentityModel
        get() = _identityModelStore.model

    private val _propertiesModel: PropertiesModel
        get() = _propertiesModelStore.model

    override fun addAlias(label: String, id: String): com.onesignal.user.IUserManager {
        Logging.log(LogLevel.DEBUG, "setAlias(label: $label, id: $id)")

        if (label == IdentityConstants.ONESIGNAL_ID) {
            throw Exception("Cannot remove '${IdentityConstants.ONESIGNAL_ID}' alias")
        }

        _identityModel[label] = id
        return this
    }

    override fun addAliases(aliases: Map<String, String>): IUserManager {
        Logging.log(LogLevel.DEBUG, "addAliases(aliases: $aliases")

        if (aliases.keys.any { it == IdentityConstants.ONESIGNAL_ID }) {
            throw Exception("Cannot remove '${IdentityConstants.ONESIGNAL_ID}' alias")
        }

        aliases.forEach {
            _identityModel[it.key] = it.value
        }

        return this
    }

    override fun removeAlias(label: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "removeAlias(label: $label)")

        if (label == IdentityConstants.ONESIGNAL_ID) {
            throw Exception("Cannot remove '${IdentityConstants.ONESIGNAL_ID}' alias")
        }

        _identityModel.remove(label)
        return this
    }

    override fun addEmailSubscription(email: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "addEmailSubscription(email: $email)")
        _subscriptionManager.addEmailSubscription(email)
        return this
    }

    override fun removeEmailSubscription(email: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "removeEmailSubscription(email: $email)")
        _subscriptionManager.removeEmailSubscription(email)
        return this
    }

    override fun addSmsSubscription(sms: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "addSmsSubscription(sms: $sms)")
        _subscriptionManager.addSmsSubscription(sms)
        return this
    }

    override fun removeSmsSubscription(sms: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "removeSmsSubscription(sms: $sms)")
        _subscriptionManager.removeSmsSubscription(sms)
        return this
    }

    override fun addTag(key: String, value: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "setTag(key: $key, value: $value)")
        _propertiesModel.tags[key] = value
        return this
    }

    override fun addTags(tags: Map<String, String>): IUserManager {
        Logging.log(LogLevel.DEBUG, "setTags(tags: $tags)")

        tags.forEach {
            _propertiesModel.tags[it.key] = it.value
        }

        return this
    }

    override fun removeTag(key: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "removeTag(key: $key)")
        _propertiesModel.tags.remove(key)
        return this
    }

    override fun removeTags(keys: Collection<String>): IUserManager {
        Logging.log(LogLevel.DEBUG, "removeTags(keys: $keys)")

        keys.forEach {
            _propertiesModel.tags.remove(it)
        }

        return this
    }
}
