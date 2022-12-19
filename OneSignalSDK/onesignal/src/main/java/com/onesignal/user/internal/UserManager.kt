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
import com.onesignal.user.subscriptions.IPushSubscription

internal open class UserManager(
    private val _subscriptionManager: ISubscriptionManager,
    private val _identityModelStore: IdentityModelStore,
    private val _propertiesModelStore: PropertiesModelStore,
    private val _languageContext: ILanguageContext
) : IUserManager {
    override var language: String
        get() = _languageContext.language
        set(value) { _languageContext.language = value }

    override val pushSubscription: IPushSubscription
        get() = _subscriptionManager.subscriptions.push

    private val _identityModel: IdentityModel
        get() = _identityModelStore.model

    private val _propertiesModel: PropertiesModel
        get() = _propertiesModelStore.model

    override fun addAlias(label: String, id: String) {
        Logging.log(LogLevel.DEBUG, "setAlias(label: $label, id: $id)")

        if (label == IdentityConstants.ONESIGNAL_ID) {
            throw Exception("Cannot remove '${IdentityConstants.ONESIGNAL_ID}' alias")
        }

        _identityModel[label] = id
    }

    override fun addAliases(aliases: Map<String, String>) {
        Logging.log(LogLevel.DEBUG, "addAliases(aliases: $aliases")

        if (aliases.keys.any { it == IdentityConstants.ONESIGNAL_ID }) {
            throw Exception("Cannot remove '${IdentityConstants.ONESIGNAL_ID}' alias")
        }

        aliases.forEach {
            _identityModel[it.key] = it.value
        }
    }

    override fun removeAlias(label: String) {
        Logging.log(LogLevel.DEBUG, "removeAlias(label: $label)")

        if (label == IdentityConstants.ONESIGNAL_ID) {
            throw Exception("Cannot remove '${IdentityConstants.ONESIGNAL_ID}' alias")
        }

        _identityModel.remove(label)
    }

    override fun addEmailSubscription(email: String) {
        Logging.log(LogLevel.DEBUG, "addEmailSubscription(email: $email)")
        _subscriptionManager.addEmailSubscription(email)
    }

    override fun removeEmailSubscription(email: String) {
        Logging.log(LogLevel.DEBUG, "removeEmailSubscription(email: $email)")
        _subscriptionManager.removeEmailSubscription(email)
    }

    override fun addSmsSubscription(sms: String) {
        Logging.log(LogLevel.DEBUG, "addSmsSubscription(sms: $sms)")
        _subscriptionManager.addSmsSubscription(sms)
    }

    override fun removeSmsSubscription(sms: String) {
        Logging.log(LogLevel.DEBUG, "removeSmsSubscription(sms: $sms)")
        _subscriptionManager.removeSmsSubscription(sms)
    }

    override fun addTag(key: String, value: String) {
        Logging.log(LogLevel.DEBUG, "setTag(key: $key, value: $value)")
        _propertiesModel.tags[key] = value
    }

    override fun addTags(tags: Map<String, String>) {
        Logging.log(LogLevel.DEBUG, "setTags(tags: $tags)")

        tags.forEach {
            _propertiesModel.tags[it.key] = it.value
        }
    }

    override fun removeTag(key: String) {
        Logging.log(LogLevel.DEBUG, "removeTag(key: $key)")
        _propertiesModel.tags.remove(key)
    }

    override fun removeTags(keys: Collection<String>) {
        Logging.log(LogLevel.DEBUG, "removeTags(keys: $keys)")

        keys.forEach {
            _propertiesModel.tags.remove(it)
        }
    }
}
