package com.onesignal.user.internal

import com.onesignal.common.OneSignalUtils
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
import com.onesignal.user.subscriptions.SubscriptionList

internal open class UserManager(
    private val _subscriptionManager: ISubscriptionManager,
    private val _identityModelStore: IdentityModelStore,
    private val _propertiesModelStore: PropertiesModelStore,
    private val _languageContext: ILanguageContext
) : IUserManager {

    val externalId: String?
        get() = _identityModel.externalId

    val tags: Map<String, String>
        get() = _propertiesModel.tags

    val aliases: Map<String, String>
        get() = _identityModel.filter { it.key != IdentityModel::id.name }.toMap()

    val subscriptions: SubscriptionList
        get() = _subscriptionManager.subscriptions

    override val pushSubscription: IPushSubscription
        get() = _subscriptionManager.subscriptions.push

    private val _identityModel: IdentityModel
        get() = _identityModelStore.model

    private val _propertiesModel: PropertiesModel
        get() = _propertiesModelStore.model

    override fun setLanguage(value: String) {
        _languageContext.language = value
    }

    override fun addAlias(label: String, id: String) {
        Logging.log(LogLevel.DEBUG, "setAlias(label: $label, id: $id)")

        if(label.isEmpty()) {
            throw Exception("Cannot add empty alias")
        }

        if (label == IdentityConstants.ONESIGNAL_ID) {
            throw Exception("Cannot add '${IdentityConstants.ONESIGNAL_ID}' alias")
        }

        _identityModel[label] = id
    }

    override fun addAliases(aliases: Map<String, String>) {
        Logging.log(LogLevel.DEBUG, "addAliases(aliases: $aliases")

        aliases.forEach {
            if(it.key.isEmpty()) {
                throw Exception("Cannot add empty alias")
            }

            if (it.key == IdentityConstants.ONESIGNAL_ID) {
                throw Exception("Cannot add '${IdentityConstants.ONESIGNAL_ID}' alias")
            }
        }

        aliases.forEach {
            _identityModel[it.key] = it.value
        }
    }

    override fun removeAlias(label: String) {
        Logging.log(LogLevel.DEBUG, "removeAlias(label: $label)")

        if(label.isEmpty()) {
            throw Exception("Cannot remove empty alias")
        }

        if (label == IdentityConstants.ONESIGNAL_ID) {
            throw Exception("Cannot remove '${IdentityConstants.ONESIGNAL_ID}' alias")
        }

        _identityModel.remove(label)
    }

    override fun removeAliases(labels: Collection<String>) {
        Logging.log(LogLevel.DEBUG, "removeAliases(labels: $labels)")

        labels.forEach {
            if(it.isEmpty()) {
                throw Exception("Cannot remove empty alias")
            }

            if (it == IdentityConstants.ONESIGNAL_ID) {
                throw Exception("Cannot remove '${IdentityConstants.ONESIGNAL_ID}' alias")
            }
        }

        labels.forEach {
            _identityModel.remove(it)
        }
    }

    override fun addEmail(email: String) {
        Logging.log(LogLevel.DEBUG, "addEmail(email: $email)")

        if(!OneSignalUtils.isValidEmail(email)) {
            throw Exception("Cannot add invalid email address as subscription: $email")
        }

        _subscriptionManager.addEmailSubscription(email)
    }

    override fun removeEmail(email: String) {
        Logging.log(LogLevel.DEBUG, "removeEmail(email: $email)")

        if(!OneSignalUtils.isValidEmail(email)) {
            throw Exception("Cannot remove invalid email address as subscription: $email")
        }

        _subscriptionManager.removeEmailSubscription(email)
    }

    override fun addSms(sms: String) {
        Logging.log(LogLevel.DEBUG, "addSms(sms: $sms)")

        if(!OneSignalUtils.isValidPhoneNumber(sms)) {
            throw Exception("Cannot add invalid sms number as subscription: $sms")
        }

        _subscriptionManager.addSmsSubscription(sms)
    }

    override fun removeSms(sms: String) {
        Logging.log(LogLevel.DEBUG, "removeSms(sms: $sms)")

        if(!OneSignalUtils.isValidPhoneNumber(sms)) {
            throw Exception("Cannot remove invalid sms number as subscription: $sms")
        }

        _subscriptionManager.removeSmsSubscription(sms)
    }

    override fun addTag(key: String, value: String) {
        Logging.log(LogLevel.DEBUG, "setTag(key: $key, value: $value)")

        if(key.isEmpty()) {
            throw Exception("Cannot add tag with empty key")
        }

        _propertiesModel.tags[key] = value
    }

    override fun addTags(tags: Map<String, String>) {
        Logging.log(LogLevel.DEBUG, "setTags(tags: $tags)")

        tags.forEach {
            if(it.key.isEmpty()) {
                throw Exception("Cannot add tag with empty key")
            }
        }

        tags.forEach {
            _propertiesModel.tags[it.key] = it.value
        }
    }

    override fun removeTag(key: String) {
        Logging.log(LogLevel.DEBUG, "removeTag(key: $key)")

        if(key.isEmpty()) {
            throw Exception("Cannot remove tag with empty key")
        }

        _propertiesModel.tags.remove(key)
    }

    override fun removeTags(keys: Collection<String>) {
        Logging.log(LogLevel.DEBUG, "removeTags(keys: $keys)")

        keys.forEach {
            if(it.isEmpty()) {
                throw Exception("Cannot remove tag with empty key")
            }
        }

        keys.forEach {
            _propertiesModel.tags.remove(it)
        }
    }
}
