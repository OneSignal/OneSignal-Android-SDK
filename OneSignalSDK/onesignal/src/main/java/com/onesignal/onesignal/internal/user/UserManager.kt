package com.onesignal.onesignal.internal.user

import com.onesignal.onesignal.internal.models.IdentityModel
import com.onesignal.onesignal.internal.models.PropertiesModel
import com.onesignal.onesignal.internal.models.SubscriptionModel
import com.onesignal.onesignal.user.IUserManager
import com.onesignal.onesignal.user.Identity
import com.onesignal.onesignal.internal.user.aliases.Alias
import com.onesignal.onesignal.internal.user.aliases.AliasCollection
import com.onesignal.onesignal.user.subscriptions.Subscription
import com.onesignal.onesignal.user.subscriptions.SubscriptionList
import com.onesignal.onesignal.internal.user.tags.Tag
import com.onesignal.onesignal.internal.user.tags.TagCollection
import com.onesignal.onesignal.internal.user.triggers.TriggerCollection

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
    override var isLocationShared: Boolean = false
    override val subscriptions: SubscriptionList = SubscriptionList()
    override val tags: TagCollection = TagCollection()
    override val aliases: AliasCollection = AliasCollection()
    override val triggers: TriggerCollection = TriggerCollection()

    //    private var userModel: UserModel
    private var _identityModel: IdentityModel = IdentityModel()
    private var _propertiesModel: PropertiesModel = PropertiesModel()
    private var _subscriptionModels: List<SubscriptionModel> = List<SubscriptionModel>()

    init {
        TODO("Populate based on initial model or would that call setUser once loaded?")
        // Following  priority order is used until we have an instance
        //   1. _user - Already set internal instance
        //   2. From local storage - restore user from last time the app opened
        //   3. Create new - Create a brand new UserAnonymous
        // TODO: This should check local storage to see if there was a user assign before
        //  val user = _user ?: UserAnonymous()
    }

    fun setUser(identityModel: IdentityModel, propertiesModel: PropertiesModel) {
        // TODO: Initialization when user is switched
    }

    override fun setExternalId(externalId: String?, externalIdAuthHash: String?): IUserManager {
        _identityModel.userId = externalId
        _identityModel.userIdAuthHash = externalIdAuthHash
        return this
    }

    override fun setAlias(alias: Alias) : com.onesignal.onesignal.user.IUserManager {
        val aliases = _identityModel.aliases.toMutableMap()
        aliases[alias.label] = alias.id
        _identityModel.aliases = aliases
        return this
    }

    override fun removeAlias(label: String): IUserManager {
        val aliases = _identityModel.aliases.toMutableMap()
        aliases.remove(label)
        _identityModel.aliases = aliases
        return this
    }

    override fun addEmailSubscription(email: String, emailAuthHash: String?): IUserManager {
        TODO("How do new models get created? Need a hook to the repo to add a new subscription model.")
        return this
    }

    override fun addSmsSubscription(sms: String): IUserManager {
        TODO("Not yet implemented")
        return this
    }

    override fun setSubscriptionEnablement(subscription: Subscription, enabled: Boolean): IUserManager {
        TODO("Not yet implemented")
        return this
    }

    override fun removeSubscription(subscription: Subscription): IUserManager {
        TODO("Not yet implemented")
        return this
    }

    override fun setTag(key: String, value: String): IUserManager {
        val tags = _propertiesModel.tags.toMutableMap()
        tags[key] = value
        _propertiesModel.tags = tags
        return this
    }

    override fun setTags(tags: Collection<Tag>): IUserManager {
        val tagCollection = _propertiesModel.tags.toMutableMap()

        tags.forEach {
            tagCollection[it.key] = it.value
        }

        _propertiesModel.tags = tagCollection
        return this
    }

    override fun removeTag(key: String): IUserManager {
        val tags = _propertiesModel.tags.toMutableMap()
        tags.remove(key)
        _propertiesModel.tags = tags
        return this
    }

    override fun removeTags(keys: Collection<String>): IUserManager {
        val tagCollection = _propertiesModel.tags.toMutableMap()

        keys.forEach {
            tagCollection.remove(it)
        }

        _propertiesModel.tags = tagCollection
        return this
    }

    override fun setTriggers(triggers: Map<String, Object>): IUserManager {
        TODO("Not yet implemented")
        return this
    }

    override fun setTrigger(key: String, value: Object): IUserManager {
        TODO("Not yet implemented")
        return this
    }

    override fun removeTriggers(keys: Collection<String>): IUserManager {
        TODO("Not yet implemented")
        return this
    }

    override fun removeTrigger(key: String): IUserManager {
        TODO("Not yet implemented")
        return this
    }

    override fun clearTriggers(): IUserManager {
        TODO("Not yet implemented")
        return this
    }

    override fun sendOutcome(name: String): IUserManager {
        TODO("Not yet implemented")
        return this
    }

    override fun sendUniqueOutcome(name: String): IUserManager {
        TODO("Not yet implemented")
        return this
    }

    override fun sendOutcomeWithValue(name: String, value: Float): IUserManager {
        TODO("Not yet implemented")
        return this
    }
}