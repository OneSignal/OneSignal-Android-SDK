package com.onesignal.user.internal

import com.onesignal.IUserJwtInvalidatedListener
import com.onesignal.UserJwtInvalidatedEvent
import com.onesignal.common.IDManager
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.events.EventProducer
import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangedArgs
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
import com.onesignal.user.internal.subscriptions.SubscriptionList
import com.onesignal.user.state.IUserStateObserver
import com.onesignal.user.state.UserChangedState
import com.onesignal.user.state.UserState
import com.onesignal.user.subscriptions.IPushSubscription

internal open class UserManager(
    private val _subscriptionManager: ISubscriptionManager,
    private val _identityModelStore: IdentityModelStore,
    private val _propertiesModelStore: PropertiesModelStore,
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

    val jwtInvalidatedCallback = EventProducer<IUserJwtInvalidatedListener>()

    private var jwtTokenInvalidated: String? = null

    override val pushSubscription: IPushSubscription
        get() = _subscriptionManager.subscriptions.push

    private val _identityModel: IdentityModel
        get() = _identityModelStore.model

    private val _propertiesModel: PropertiesModel
        get() = _propertiesModelStore.model

    override fun setLanguage(value: String) {
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

        if (label.isEmpty()) {
            Logging.log(LogLevel.ERROR, "Cannot add empty alias")
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

        aliases.forEach {
            if (it.key.isEmpty()) {
                Logging.log(LogLevel.ERROR, "Cannot add empty alias")
                return
            }

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

        if (label.isEmpty()) {
            Logging.log(LogLevel.ERROR, "Cannot remove empty alias")
            return
        }

        if (label == IdentityConstants.ONESIGNAL_ID) {
            Logging.log(LogLevel.ERROR, "Cannot remove '${IdentityConstants.ONESIGNAL_ID}' alias")
            return
        }

        _identityModel.remove(label)
    }

    override fun removeAliases(labels: Collection<String>) {
        Logging.log(LogLevel.DEBUG, "removeAliases(labels: $labels)")

        labels.forEach {
            if (it.isEmpty()) {
                Logging.log(LogLevel.ERROR, "Cannot remove empty alias")
                return
            }

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

        if (key.isEmpty()) {
            Logging.log(LogLevel.ERROR, "Cannot add tag with empty key")
            return
        }

        _propertiesModel.tags[key] = value
    }

    override fun addTags(tags: Map<String, String>) {
        Logging.log(LogLevel.DEBUG, "setTags(tags: $tags)")

        tags.forEach {
            if (it.key.isEmpty()) {
                Logging.log(LogLevel.ERROR, "Cannot add tag with empty key")
                return
            }
        }

        tags.forEach {
            _propertiesModel.tags[it.key] = it.value
        }
    }

    override fun removeTag(key: String) {
        Logging.log(LogLevel.DEBUG, "removeTag(key: $key)")

        if (key.isEmpty()) {
            Logging.log(LogLevel.ERROR, "Cannot remove tag with empty key")
            return
        }

        _propertiesModel.tags.remove(key)
    }

    override fun removeTags(keys: Collection<String>) {
        Logging.log(LogLevel.DEBUG, "removeTags(keys: $keys)")

        keys.forEach {
            if (it.isEmpty()) {
                Logging.log(LogLevel.ERROR, "Cannot remove tag with empty key")
                return
            }
        }

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

    override fun addUserJwtInvalidatedListener(listener: IUserJwtInvalidatedListener) {
        Logging.debug("OneSignal.addUserJwtInvalidatedListener(listener: $listener)")
        jwtInvalidatedCallback.subscribe(listener)
    }

    override fun removeUserJwtInvalidatedListener(listener: IUserJwtInvalidatedListener) {
        Logging.debug("OneSignal.removeUserJwtInvalidatedListener(listener: $listener)")
        jwtInvalidatedCallback.unsubscribe(listener)
    }

    override fun onModelReplaced(
        model: IdentityModel,
        tag: String,
    ) { }

    override fun onModelUpdated(
        args: ModelChangedArgs,
        tag: String,
    ) {
        when (args.property) {
            IdentityConstants.ONESIGNAL_ID -> {
                val newUserState = UserState(args.newValue.toString(), externalId)
                this.changeHandlersNotifier.fire {
                    it.onUserStateChange(UserChangedState(newUserState))
                }
            }
            IdentityConstants.JWT_TOKEN -> {
                // Fire the event when the JWT has been invalidated.
                val oldJwt = args.oldValue.toString()
                val newJwt = args.newValue.toString()

                // When newJwt is equals to null, we are invalidating JWT for the current user.
                // We need to prevent same JWT from being invalidated twice in a row.
                if (jwtTokenInvalidated != oldJwt && newJwt == null) {
                    jwtInvalidatedCallback.fire {
                        it.onUserJwtInvalidated(UserJwtInvalidatedEvent(externalId))
                    }
                }

                jwtTokenInvalidated = oldJwt
            }
        }
    }
}
