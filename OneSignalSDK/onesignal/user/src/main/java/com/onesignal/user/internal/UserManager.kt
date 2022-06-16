package com.onesignal.user.internal

import com.onesignal.user.IUserManager
import com.onesignal.user.Identity
import com.onesignal.user.aliases.Alias
import com.onesignal.user.aliases.AliasCollection
import com.onesignal.user.internal.models.UserModel
import com.onesignal.user.subscriptions.Subscription
import com.onesignal.user.subscriptions.SubscriptionCollection
import com.onesignal.user.tags.Tag
import com.onesignal.user.tags.TagCollection
import com.onesignal.user.triggers.TriggerCollection

open class UserManager(
        private val model: UserModel,
        override val identity: Identity
        ) : IUserManager {

    override var language: String
        get() = model.language
        set(value) { model.language = value }

    override var privacyConsent: Boolean = false
    override var isLocationShared: Boolean = false
    override val subscriptions: SubscriptionCollection = SubscriptionCollection(listOf())
    override val tags: TagCollection = TagCollection(listOf())
    override val aliases: AliasCollection = AliasCollection(listOf())
    override val triggers: TriggerCollection = TriggerCollection(listOf())

    init {
        TODO("Populate based on incoming model?")
    }
    override fun setExternalId(externalId: String?, externalIdAuthHash: String?): IUserManager {
        TODO("Not yet implemented")
        return this
    }

    override fun addAlias(alias: Alias): IUserManager {
        TODO("Not yet implemented")
        return this
    }

    override fun addAliases(aliases: Collection<Alias>): IUserManager {
        TODO("Not yet implemented")
        return this
    }

    override fun removeAlias(label: String): IUserManager {
        TODO("Not yet implemented")
        return this
    }

    override fun removeAlias(alias: Alias): IUserManager {
        TODO("Not yet implemented")
        return this
    }

    override fun updateAlias(alias: Alias, newId: String): IUserManager {
        TODO("Not yet implemented")
        return this
    }

    override fun addEmailSubscription(email: String, emailAuthHash: String?): IUserManager {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
        return this
    }

    override fun setTags(keys: Collection<Tag>): IUserManager {
        TODO("Not yet implemented")
        return this
    }

    override fun removeTag(key: String): IUserManager {
        TODO("Not yet implemented")
        return this
    }

    override fun removeTags(keys: Collection<String>): IUserManager {
        TODO("Not yet implemented")
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