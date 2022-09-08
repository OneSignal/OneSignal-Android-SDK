package com.onesignal.core.user

import com.onesignal.core.OneSignal
import com.onesignal.core.user.subscriptions.SubscriptionList

/**
 * The OneSignal user manager is responsible for managing the current user state.  When
 * an app starts up for the first time, it is defaulted to having a user with an [identity]
 * of [Identity.Anonymous].  Once the application knows the identity of the user using their
 * app they should call [OneSignal.login] providing that identity to OneSignal, at which
 * point all state in here will reflect the state of that known user.
 *
 * The current user is persisted across the application lifecycle, even when the application
 * is restarted.  It is up to the application developer to call [OneSignal.login] when
 * the user of the application switches, or logs out, to ensure the identity tracked by OneSignal
 * remains in sync.
 *
 * When you should call [OneSignal.login]:
 *   1. When the identity of the user changes (i.e. a login or a context switch)
 *   2. When the identity of the user is lost (i.e. a logout)
 */
interface IUserManager {
    /**
     * The external id of the current user.  When null, the current user is a device-scoped user
     * and cannot be retrieved outside of this device/app.
     */
    val externalId: String?

    /**
     * The language string for the current user.
     */
    var language: String

    /**
     * The aliases associated to the current user.
     */
    val aliases: Map<String, String>

    /**
     * The subscriptions associated to the current user.
     */
    val subscriptions: SubscriptionList

    /**
     * The tags associated to the current user.
     */
    val tags: Map<String, String>

    /**
     * Set an alias for the current user.  If this alias already exists it will be overwritten.
     *
     * @param label The alias label that you want to set against the current user.
     * @param id The alias id that should be set against the current user. This must be a unique value
     * within the alias label across your entire user base so it can uniquely identify this user.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun addAlias(label: String, id: String): IUserManager

    /**
     * Add/set aliases for the current user. If any alias already exists it will be overwritten.
     *
     * @param aliases A map of the alias label -> alias id that should be set against the user. Each
     * alias id must be a unique value within the alias label across your entire user base so it can
     * uniquely identify this user.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun addAliases(aliases: Map<String, String>): IUserManager

    /**
     * Remove an alias from the current user.
     *
     * @param label The alias label that should no longer be set for the current user.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun removeAlias(label: String): IUserManager

    /**
     * Add a new email subscription to the current user.
     *
     * @param email The email address that the current user has subscribed for.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun addEmailSubscription(email: String): IUserManager

    /**
     * Remove an email subscription from the current user.
     *
     * @param email The email address that the current user was subscribed for, and should no longer be.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun removeEmailSubscription(email: String): IUserManager

    /**
     * Add a new SMS subscription to the current user.
     *
     * @param sms The phone number that the current user has subscribed for, in [E.164](https://documentation.onesignal.com/docs/sms-faq#what-is-the-e164-format) format.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun addSmsSubscription(sms: String): IUserManager

    /**
     * Remove an SMS subscription from the current user.
     *
     * @param sms The sms address that the current user was subscribed for, and should no longer be.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun removeSmsSubscription(sms: String): IUserManager

    /**
     * Set a tag for the current user.  Tags are key:value pairs used as building blocks
     * for targeting specific users and/or personalizing messages. See [Data Tags | OneSignal](https://documentation.onesignal.com/docs/add-user-data-tags).
     *
     * If the tag key already exists, it will be replaced with the value provided here.
     *
     * @param key The key of the data tag.
     * @param value THe new value of the data tag.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun setTag(key: String, value: String): IUserManager

    /**
     * Set multiple tags for the current user.  Tags are key:value pairs used as building blocks
     * for targeting specific users and/or personalizing messages. See [Data Tags | OneSignal](https://documentation.onesignal.com/docs/add-user-data-tags).
     *
     * If the tag key already exists, it will be replaced with the value provided here.
     *
     * @param tags A map of tags, all of which will be added/updated for the current user.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun setTags(tags: Map<String, String>): IUserManager

    /**
     * Remove the data tag with the provided key from the current user.
     *
     * @param key The key of the data tag.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun removeTag(key: String): IUserManager

    /**
     * Remove multiple tags from the current user.
     *
     * @param keys The collection of keys, all of which will be removed from the current user.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun removeTags(keys: Collection<String>): IUserManager

    /**
     * Set a trigger for the current user.  Triggers are currently explicitly used to determine
     * whether a specific IAM should be displayed to the user. See [Triggers | OneSignal](https://documentation.onesignal.com/docs/iam-triggers).
     *
     * If the trigger key already exists, it will be replaced with the value provided here. Note that
     * triggers are not persisted to the backend. They only exist on the local device and are applicable
     * to the current user.
     *
     * @param key The key of the trigger that is to be set.
     * @param value The value of the trigger. Although this is defined as [Any] its [Any.toString]
     * will be used for evaluation purposes.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun setTrigger(key: String, value: Any): IUserManager

    /**
     * Set multiple triggers for the current user.  Triggers are currently explicitly used to determine
     * whether a specific IAM should be displayed to the user. See [Triggers | OneSignal](https://documentation.onesignal.com/docs/iam-triggers).
     *
     * If the trigger key already exists, it will be replaced with the value provided here.  Note that
     * triggers are not persisted to the backend. They only exist on the local device and are applicable
     * to the current user.
     *
     * @param triggers The map of triggers that are to be added to the current user.  Although the
     * value of the [Map] is defined as [Any] its [Any.toString] will be used for evaluation
     * purposes.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun setTriggers(triggers: Map<String, Any>): IUserManager

    /**
     * Remove the trigger with the provided key from the current user.
     *
     * @param key The key of the trigger.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun removeTrigger(key: String): IUserManager

    /**
     * Remove multiple triggers from the current user.
     *
     * @param keys The collection of keys, all of which will be removed from the current user.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun removeTriggers(keys: Collection<String>): IUserManager

    /**
     * Clear all triggers from the current user.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun clearTriggers(): IUserManager

    /**
     * Send an outcome with the provided name, captured against the current user (ish).
     * See [Outcomes | OneSignal] (https://documentation.onesignal.com/docs/outcomes)
     *
     * @param name The name of the outcome that has occurred.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun sendOutcome(name: String): IUserManager

    /**
     * Send a unique outcome with the provided name, captured against the current user (ish).
     * See [Outcomes | OneSignal] (https://documentation.onesignal.com/docs/outcomes)
     *
     * @param name The name of the unique outcome that has occurred.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun sendUniqueOutcome(name: String): IUserManager

    /**
     * Send an outcome with the provided name and value, captured against the current user (ish).
     * See [Outcomes | OneSignal] (https://documentation.onesignal.com/docs/outcomes)
     *
     * @param name The name of the outcome that has occurred.
     * @param value The value tied to the outcome.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun sendOutcomeWithValue(name: String, value: Float): IUserManager
}
