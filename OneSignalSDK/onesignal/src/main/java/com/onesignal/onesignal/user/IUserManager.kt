package com.onesignal.onesignal.user

import com.onesignal.onesignal.OneSignal
import com.onesignal.onesignal.user.subscriptions.ISubscription
import com.onesignal.onesignal.user.subscriptions.SubscriptionList

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
     * The identity of the current user.
     */
    val identity: Identity

    /**
     * The language string for the current user.
     */
    var language: String

    /**
     * Indicates whether the current user has consented. This field is only relevant when
     * the application has opted into data privacy protections. See [OneSignal.requiresPrivacyConsent].
     */
    var privacyConsent: Boolean

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
     * Change the external ID of the current user.  This will update the current user's external id
     * It will not switch users, to switch users you must call [OneSignal.login].
     *
     * @param externalId The new external ID for the current user. Set to `null` to remove the
     * external ID for the current user (TODO: does this make them anonymous?)
     * @param externalIdAuthHash The optional auth hash for the external id. If not using identity
     * verification, this can be omitted or set to `null`. See [Identity Verification | OneSignal](https://documentation.onesignal.com/docs/identity-verification)
     */
    fun setExternalId(externalId: String?) : IUserManager
    fun setExternalId(externalId: String?, externalIdAuthHash: String? = null) : IUserManager

    /**
     * Set an alias for the current user.  If this alias already exists it will be overwritten.
     *
     * @param label The alias label that you want to set against the current user.
     * @param id The alias id that should be set against the current user. This must be a unique value
     * within the alias label across your entire user base so it can uniquely identify this user.
     */
    fun setAlias(label: String, id: String) : IUserManager

    /**
     * Remove an alias from the current user.
     *
     * @param label The alias label that should no longer be set for the current user.
     */
    fun removeAlias(label: String) : IUserManager

    /**
     * Add a new email subscription to the current user.
     *
     * @param email The email address that the current user has subscribed for.
     * @param emailAuthHash The optional auth hash for the email. If not using identity verification,
     * this can be omitted or set to `null`. See [Identity Verification | OneSignal](https://documentation.onesignal.com/docs/identity-verification)
     */
    fun addEmailSubscription(email: String, emailAuthHash: String? = null) : IUserManager
    fun addEmailSubscription(email: String) : IUserManager = addEmailSubscription(email, null)

    /**
     * Add a new SMS subscription to the current user.
     *
     * @param sms The phone number that the current user has subscribed for, in [E.164](https://documentation.onesignal.com/docs/sms-faq#what-is-the-e164-format) format.
     * @param emailAuthHash The optional auth hash for the email. If not using identity verification,
     * this can be omitted or set to `null`. See [Identity Verification | OneSignal](https://documentation.onesignal.com/docs/identity-verification)
     */
    fun addSmsSubscription(sms: String, smsAuthHash: String? = null) : IUserManager
    fun addSmsSubscription(sms: String) : IUserManager = addSmsSubscription(sms, null)

    /**
     * Add this device as a push subscriber for this user.
     */
    fun addPushSubscription() : IUserManager

    /**
     * Change the enablement of the provided subscription.  The subscription will still exist
     * against the user, it will however no longer receive notifications.
     *
     * @param subscription The subscription whose enablement should be updated. This is obtained within
     * the [subscriptions] collection.
     *
     * @param enabled Whether the subscription should be enabled (`true`) or disabled (`false`).
     */
    fun setSubscriptionEnablement(subscription: ISubscription, enabled: Boolean) : IUserManager

    /**
     * Remove the subscription from the current user. The subscription will be deleted as a
     * record.
     *
     * @param subscription The subscription that is to be removed. This is obtained within
     * the [subscriptions] collection.
     */
    fun removeSubscription(subscription: ISubscription) : IUserManager

    /**
     * Set a tag for the current user.  Tags are key:value pairs used as building blocks
     * for targeting specific users and/or personalizing messages. See [Data Tags | OneSignal](https://documentation.onesignal.com/docs/add-user-data-tags).
     *
     * If the tag key already exists, it will be replaced with the value provided here.
     *
     * @param key The key of the data tag.
     * @param value THe new value of the data tag.
     */
    fun setTag(key: String, value: String) : IUserManager

    /**
     * Set multiple tags for the current user.  Tags are key:value pairs used as building blocks
     * for targeting specific users and/or personalizing messages. See [Data Tags | OneSignal](https://documentation.onesignal.com/docs/add-user-data-tags).
     *
     * If the tag key already exists, it will be replaced with the value provided here.
     *
     * @param tags A map of tags, all of which will be added/updated for the current user.
     */
    fun setTags(tags: Map<String, String>) : IUserManager

    /**
     * Remove the data tag with the provided key from the current user.
     *
     * @param key The key of the data tag.
     */
    fun removeTag(key: String) : IUserManager

    /**
     * Remove multiple tags from the current user.
     *
     * @param keys The collection of keys, all of which will be removed from the current user.
     */
    fun removeTags(keys: Collection<String>) : IUserManager

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
     */
    fun setTrigger(key: String, value: Any) : IUserManager

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
     */
    fun setTriggers(triggers: Map<String, Any>) : IUserManager

    /**
     * Remove the trigger with the provided key from the current user.
     *
     * @param key The key of the trigger.
     */
    fun removeTrigger(key: String) : IUserManager

    /**
     * Remove multiple triggers from the current user.
     *
     * @param keys The collection of keys, all of which will be removed from the current user.
     */
    fun removeTriggers(keys: Collection<String>) : IUserManager

    /**
     * Clear all triggers from the current user.
     */
    fun clearTriggers() : IUserManager

    /**
     * Send an outcome with the provided name, captured against the current user (ish).
     * See [Outcomes | OneSignal] (https://documentation.onesignal.com/docs/outcomes)
     *
     * @param name The name of the outcome that has occurred.
     */
    fun sendOutcome(name: String) : IUserManager

    /**
     * Send a unique outcome with the provided name, captured against the current user (ish).
     * See [Outcomes | OneSignal] (https://documentation.onesignal.com/docs/outcomes)
     *
     * @param name The name of the unique outcome that has occurred.
     */
    fun sendUniqueOutcome(name: String) : IUserManager

    /**
     * Send an outcome with the provided name and value, captured against the current user (ish).
     * See [Outcomes | OneSignal] (https://documentation.onesignal.com/docs/outcomes)
     *
     * @param name The name of the outcome that has occurred.
     * @param value The value tied to the outcome.
     */
    fun sendOutcomeWithValue(name: String, value: Float) : IUserManager
}
