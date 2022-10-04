package com.onesignal.core.internal.user

import com.onesignal.core.debug.LogLevel
import com.onesignal.core.internal.backend.IdentityConstants
import com.onesignal.core.internal.common.suspendifyOnThread
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.models.IdentityModel
import com.onesignal.core.internal.models.IdentityModelStore
import com.onesignal.core.internal.models.PropertiesModel
import com.onesignal.core.internal.models.PropertiesModelStore
import com.onesignal.core.internal.models.TriggerModel
import com.onesignal.core.internal.models.TriggerModelStore
import com.onesignal.core.internal.outcomes.IOutcomeEventsController
import com.onesignal.core.user.IUserManager
import com.onesignal.core.user.subscriptions.SubscriptionList

internal open class UserManager(
    private val _subscriptionManager: ISubscriptionManager,
    private val _identityModelStore: IdentityModelStore,
    private val _propertiesModelStore: PropertiesModelStore,
    private val _triggerModelStore: TriggerModelStore,
    private val _outcomeController: IOutcomeEventsController
) : IUserManager {

    override val externalId: String?
        get() = _identityModel.externalId

    override var language: String
        get() = _propertiesModel.language
        set(value) { _propertiesModel.language = value }

    override val tags: Map<String, String>
        get() = _propertiesModel.tags

    override val aliases: Map<String, String>
        get() = _identityModel.filter { it.key != IdentityModel::id.name }.toMap()

    override val subscriptions: SubscriptionList
        get() = _subscriptionManager.subscriptions

    private val _identityModel: IdentityModel
        get() = _identityModelStore.get()

    private val _propertiesModel: PropertiesModel
        get() = _propertiesModelStore.get()

    override fun addAlias(label: String, id: String): com.onesignal.core.user.IUserManager {
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

    override fun setTag(key: String, value: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "setTag(key: $key, value: $value)")
        _propertiesModel.tags[key] = value
        return this
    }

    override fun setTags(tags: Map<String, String>): IUserManager {
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

    override fun setTriggers(triggers: Map<String, Any>): IUserManager {
        Logging.log(LogLevel.DEBUG, "setTriggers(triggers: $triggers)")

        triggers.forEach { setTrigger(it.key, it.value) }

        return this
    }

    override fun setTrigger(key: String, value: Any): IUserManager {
        Logging.log(LogLevel.DEBUG, "setTrigger(key: $key, value: $value)")

        var triggerModel = _triggerModelStore.get(key)
        if (triggerModel != null) {
            triggerModel.value = value
        } else {
            triggerModel = TriggerModel()
            triggerModel.id = key
            triggerModel.key = key
            triggerModel.value = value
            _triggerModelStore.add(triggerModel)
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

        suspendifyOnThread {
            _outcomeController.sendOutcomeEvent(name)
        }

        return this
    }

    override fun sendUniqueOutcome(name: String): IUserManager {
        Logging.log(LogLevel.DEBUG, "sendUniqueOutcome(name: $name)")

        suspendifyOnThread {
            _outcomeController.sendUniqueOutcomeEvent(name)
        }

        return this
    }

    override fun sendOutcomeWithValue(name: String, value: Float): IUserManager {
        Logging.log(LogLevel.DEBUG, "sendOutcomeWithValue(name: $name, value: $value)")

        suspendifyOnThread {
            _outcomeController.sendOutcomeEventWithValue(name, value)
        }

        return this
    }
}
