package com.onesignal.user

import com.onesignal.user.aliases.Alias
import com.onesignal.user.aliases.AliasCollection
import com.onesignal.user.subscriptions.Subscription
import com.onesignal.user.subscriptions.SubscriptionCollection
import com.onesignal.user.tags.Tag
import com.onesignal.user.tags.TagCollection
import com.onesignal.user.triggers.TriggerCollection

/**
 * A OneSignal user
 */
interface IUserManager {
    /**
     * The identity of this user.
     */
    val identity: Identity

    /**
     * The language string for this user
     */
    var language: String

    /**
     * Indicates whether this user has consented. This field is only relevant when
     * the application has opted into data privacy protections.
     */
    var privacyConsent: Boolean


    /**
     * Whether the user has enabled location sharing (may or may not be enabled on
     * the current device)
     */
    var isLocationShared: Boolean


    /**
     * The subscriptions associated to this user.
     */
    val subscriptions: SubscriptionCollection

    /**
     * The tags associated to this user.
     */
    val tags: TagCollection

    /**
     * The aliases for this user.
     */
    val aliases: AliasCollection

    /**
     * The triggers for this user.
     */
    val triggers: TriggerCollection

    /** USER OPERATIONS **/
    fun setExternalId(externalId: String?, externalIdAuthHash: String?) : IUserManager

    /** ALIAS OPERATIONS **/
    fun addAlias(alias: Alias) : IUserManager
    fun addAliases(aliases: Collection<Alias>) : IUserManager
    fun removeAlias(label: String) : IUserManager
    fun removeAlias(alias: Alias) : IUserManager
    fun updateAlias(alias: Alias, newId: String) : IUserManager

    /** SUBSCRIPTION OPERATIONS **/
    fun addEmailSubscription(email: String, emailAuthHash: String?) : IUserManager
    fun addSmsSubscription(sms: String) : IUserManager
    fun setSubscriptionEnablement(subscription: Subscription, enabled: Boolean) : IUserManager
    fun removeSubscription(subscription: Subscription) : IUserManager

    /** TAG OPERATIONS **/
    fun setTag(key: String, value: String) : IUserManager
    fun setTags(keys: Collection<Tag>) : IUserManager
    fun removeTag(key: String) : IUserManager
    fun removeTags(keys: Collection<String>) : IUserManager

    /** TRIGGER OPERATIONS **/
    fun setTriggers(triggers: Map<String, Object>) : IUserManager
    fun setTrigger(key: String, value: Object) : IUserManager
    fun removeTriggers(keys: Collection<String>) : IUserManager
    fun removeTrigger(key: String) : IUserManager
    fun clearTriggers() : IUserManager

    /**
     * Send an outcome with the provided name, captured against the current user (ish).
     *
     * @param name The name of the outcome that has occurred.
     */
    fun sendOutcome(name: String) : IUserManager

    /**
     * Send a unique outcome with the provided name, captured against the current user (ish).
     *
     * @param name The name of the unique outcome that has occurred.
     */
    fun sendUniqueOutcome(name: String) : IUserManager

    /**
     * Send an outcome with the provided name and value, captured against the current user (ish).
     *
     * @param name The name of the outcome that has occurred.
     * @param value The value tied to the outcome.
     */
    fun sendOutcomeWithValue(name: String, value: Float) : IUserManager
}
