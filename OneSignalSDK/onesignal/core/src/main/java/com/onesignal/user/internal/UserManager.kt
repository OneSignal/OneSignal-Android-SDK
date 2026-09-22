package com.onesignal.user.internal

import com.onesignal.common.IDManager
import com.onesignal.common.JSONUtils
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.events.EventProducer
import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.common.rejectNullOrEmpty
import com.onesignal.common.rejectNullOrEmptyAny
import com.onesignal.common.rejectNullOrEmptyEntries
import com.onesignal.core.internal.language.ILanguageContext
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.IUserManager
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.customEvents.ICustomEventController
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.properties.PropertiesModel
import com.onesignal.user.internal.properties.PropertiesModelStore
import com.onesignal.user.internal.subscriptions.ISubscriptionManager
import com.onesignal.user.internal.subscriptions.SubscriptionList
import com.onesignal.user.state.IUserStateObserver
import com.onesignal.user.state.UserChangedState
import com.onesignal.user.state.UserState
import com.onesignal.user.subscriptions.IPushSubscription

internal open class UserManager(
    private val _subscriptionManager: ISubscriptionManager,
    private val _identityModelStore: IdentityModelStore,
    private val _propertiesModelStore: PropertiesModelStore,
    private val _customEventController: ICustomEventController,
    private val _languageContext: ILanguageContext,
) : IUserManager, ISingletonModelStoreChangeHandler<IdentityModel> {
    override val onesignalId: String
        get() = if (IDManager.isLocalId(_identityModel.onesignalId)) "" else _identityModel.onesignalId

    override val externalId: String
        get() = _identityModel.externalId ?: ""

    val aliases: Map<String, String>
        get() = _identityModel.filter { it.key != IdentityModel::id.name }.toMap()

    val subscriptions: SubscriptionList
        get() = _subscriptionManager.subscriptions

    val changeHandlersNotifier = EventProducer<IUserStateObserver>()

    override val pushSubscription: IPushSubscription
        get() = _subscriptionManager.subscriptions.push

    private val _identityModel: IdentityModel
        get() = _identityModelStore.model

    private val _propertiesModel: PropertiesModel
        get() = _propertiesModelStore.model

    override fun setLanguage(value: String) {
        if (rejectNullOrEmpty(value, "setLanguage: language")) return
        _languageContext.language = value
    }

    init {
        _identityModelStore.subscribe(this)
    }

    override fun addAlias(
        label: String,
        id: String,
    ) {
        Logging.log(LogLevel.DEBUG, "setAlias(label: $label, id: $id)")

        if (rejectNullOrEmpty(label, "addAlias: label") || rejectNullOrEmpty(id, "addAlias: id")) {
            return
        }

        if (label == IdentityConstants.ONESIGNAL_ID) {
            Logging.log(LogLevel.ERROR, "Cannot add '${IdentityConstants.ONESIGNAL_ID}' alias")
            return
        }

        _identityModel[label] = id
    }

    override fun addAliases(aliases: Map<String, String>) {
        Logging.log(LogLevel.DEBUG, "addAliases(aliases: $aliases")

        if (rejectNullOrEmptyEntries(aliases, "addAliases", allowEmptyValue = false)) return

        aliases.forEach {
            if (it.key == IdentityConstants.ONESIGNAL_ID) {
                Logging.log(LogLevel.ERROR, "Cannot add '${IdentityConstants.ONESIGNAL_ID}' alias")
                return
            }
        }

        aliases.forEach {
            _identityModel[it.key] = it.value
        }
    }

    override fun removeAlias(label: String) {
        Logging.log(LogLevel.DEBUG, "removeAlias(label: $label)")

        if (rejectNullOrEmpty(label, "removeAlias: label")) return

        if (label == IdentityConstants.ONESIGNAL_ID) {
            Logging.log(LogLevel.ERROR, "Cannot remove '${IdentityConstants.ONESIGNAL_ID}' alias")
            return
        }

        _identityModel.remove(label)
    }

    override fun removeAliases(labels: Collection<String>) {
        Logging.log(LogLevel.DEBUG, "removeAliases(labels: $labels)")

        if (rejectNullOrEmptyAny(labels, "removeAliases: label")) return

        labels.forEach {
            if (it == IdentityConstants.ONESIGNAL_ID) {
                Logging.log(LogLevel.ERROR, "Cannot remove '${IdentityConstants.ONESIGNAL_ID}' alias")
                return
            }
        }

        labels.forEach {
            _identityModel.remove(it)
        }
    }

    override fun addEmail(email: String) {
        Logging.log(LogLevel.DEBUG, "addEmail(email: $email)")

        if (!OneSignalUtils.isValidEmail(email)) {
            Logging.log(LogLevel.ERROR, "Cannot add invalid email address as subscription: $email")
            return
        }

        _subscriptionManager.addEmailSubscription(email)
    }

    override fun removeEmail(email: String) {
        Logging.log(LogLevel.DEBUG, "removeEmail(email: $email)")

        if (!OneSignalUtils.isValidEmail(email)) {
            Logging.log(LogLevel.ERROR, "Cannot remove invalid email address as subscription: $email")
            return
        }

        _subscriptionManager.removeEmailSubscription(email)
    }

    override fun addSms(sms: String) {
        Logging.log(LogLevel.DEBUG, "addSms(sms: $sms)")

        if (!OneSignalUtils.isValidPhoneNumber(sms)) {
            Logging.log(LogLevel.ERROR, "Cannot add invalid sms number as subscription: $sms")
            return
        }

        _subscriptionManager.addSmsSubscription(sms)
    }

    override fun removeSms(sms: String) {
        Logging.log(LogLevel.DEBUG, "removeSms(sms: $sms)")

        if (!OneSignalUtils.isValidPhoneNumber(sms)) {
            Logging.log(LogLevel.ERROR, "Cannot remove invalid sms number as subscription: $sms")
            return
        }

        _subscriptionManager.removeSmsSubscription(sms)
    }

    override fun addTag(
        key: String,
        value: String,
    ) {
        Logging.log(LogLevel.DEBUG, "setTag(key: $key, value: $value)")

        if (rejectNullOrEmpty(key, "addTag: key")) return

        _propertiesModel.tags[key] = value
    }

    override fun addTags(tags: Map<String, String>) {
        Logging.log(LogLevel.DEBUG, "setTags(tags: $tags)")

        if (rejectNullOrEmptyEntries(tags, "addTags", allowEmptyValue = true)) return

        tags.forEach {
            _propertiesModel.tags[it.key] = it.value
        }
    }

    override fun removeTag(key: String) {
        Logging.log(LogLevel.DEBUG, "removeTag(key: $key)")

        if (rejectNullOrEmpty(key, "removeTag: key")) return

        _propertiesModel.tags.remove(key)
    }

    override fun removeTags(keys: Collection<String>) {
        Logging.log(LogLevel.DEBUG, "removeTags(keys: $keys)")

        if (rejectNullOrEmptyAny(keys, "removeTags: key")) return

        keys.forEach {
            _propertiesModel.tags.remove(it)
        }
    }

    override fun getTags(): Map<String, String> {
        return _propertiesModel.tags.toMap()
    }

    override fun addObserver(observer: IUserStateObserver) {
        changeHandlersNotifier.subscribe(observer)
    }

    override fun removeObserver(observer: IUserStateObserver) {
        changeHandlersNotifier.unsubscribe(observer)
    }

    override fun trackEvent(
        name: String,
        properties: Map<String, Any?>?,
    ) {
        if (rejectNullOrEmpty(name, "trackEvent: name")) return
        if (!JSONUtils.isValidJsonObject(properties)) {
            Logging.log(LogLevel.ERROR, "Custom event properties are not JSON-serializable")
            return
        }

        _customEventController.sendCustomEvent(name, properties)
    }

    override fun onModelReplaced(
        model: IdentityModel,
        tag: String,
    ) { }

    override fun onModelUpdated(
        args: ModelChangedArgs,
        tag: String,
    ) {
        if (args.property == IdentityConstants.ONESIGNAL_ID) {
            val newUserState = UserState(args.newValue.toString(), externalId)
            this.changeHandlersNotifier.fire {
                it.onUserStateChange(UserChangedState(newUserState))
            }
        }
    }
}
