package com.onesignal.user

import com.onesignal.OneSignal
import com.onesignal.user.state.IUserStateObserver
import com.onesignal.user.subscriptions.IPushSubscription

/**
 * The OneSignal user manager is responsible for managing the current user state.  When
 * an app starts up for the first time, it is defaulted to having a guest user that is only
 * accessible by the current device.  Once the application knows the identity of the user using their
 * app, they should call [OneSignal.login] providing that identity to OneSignal, at which
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
     * The push subscription associated to the current user.
     */
    val pushSubscription: IPushSubscription

    /**
     * The UUID generated by OneSignal to represent a user, empty if this is currently unavailable
     */
    val onesignalId: String

    /**
     * The External ID is OneSignal's default and recommended alias label. This should be the main
     * identifier you use to identify users. It is set when calling the [OneSignal.login] method.
     *
     * This is empty if the External ID has not been set.
     */
    val externalId: String

    /**
     * Set the 2-character language either as a detected language or explicitly set for this user. See
     * See [Supported Languages | OneSignal](https://documentation.onesignal.com/docs/language-localization#what-languages-are-supported)
     *
     * @param value The 2-character language string, or an empty string to use the device default.
     */
    fun setLanguage(value: String)

    /**
     * Set an alias for the current user.  If this alias already exists it will be overwritten.
     *
     * @param label The alias label that you want to set against the current user.
     * @param id The alias id that should be set against the current user. This must be a unique value
     * within the alias label across your entire user base so it can uniquely identify this user.
     */
    fun addAlias(
        label: String,
        id: String,
    )

    /**
     * Add/set aliases for the current user. If any alias already exists it will be overwritten.
     *
     * @param aliases A map of the alias label -> alias id that should be set against the user. Each
     * alias id must be a unique value within the alias label across your entire user base so it can
     * uniquely identify this user.
     */
    fun addAliases(aliases: Map<String, String>)

    /**
     * Remove an alias from the current user.
     *
     * @param label The alias label that should no longer be set for the current user.
     */
    fun removeAlias(label: String)

    /**
     * Remove multiple aliases from the current user.
     *
     * @param labels The collection of alias labels, all of which will be removed from the current user.
     */
    fun removeAliases(labels: Collection<String>)

    /**
     * Add a new email subscription to the current user.
     *
     * @param email The email address that the current user has subscribed for.
     */
    fun addEmail(email: String)

    /**
     * Remove an email subscription from the current user.
     *
     * @param email The email address that the current user was subscribed for, and should no longer be.
     */
    fun removeEmail(email: String)

    /**
     * Add a new SMS subscription to the current user.
     *
     * @param sms The phone number that the current user has subscribed for, in [E.164](https://documentation.onesignal.com/docs/sms-faq#what-is-the-e164-format) format.
     */
    fun addSms(sms: String)

    /**
     * Remove an SMS subscription from the current user.
     *
     * @param sms The sms address that the current user was subscribed for, and should no longer be.
     */
    fun removeSms(sms: String)

    /**
     * Add a tag for the current user.  Tags are key:value pairs used as building blocks
     * for targeting specific users and/or personalizing messages. See [Data Tags | OneSignal](https://documentation.onesignal.com/docs/add-user-data-tags).
     *
     * If the tag key already exists, it will be replaced with the value provided here.
     *
     * @param key The key of the data tag.
     * @param value THe new value of the data tag.
     */
    fun addTag(
        key: String,
        value: String,
    )

    /**
     * Add multiple tags for the current user.  Tags are key:value pairs used as building blocks
     * for targeting specific users and/or personalizing messages. See [Data Tags | OneSignal](https://documentation.onesignal.com/docs/add-user-data-tags).
     *
     * If the tag key already exists, it will be replaced with the value provided here.
     *
     * @param tags A map of tags, all of which will be added/updated for the current user.
     */
    fun addTags(tags: Map<String, String>)

    /**
     * Remove the data tag with the provided key from the current user.
     *
     * @param key The key of the data tag.
     */
    fun removeTag(key: String)

    /**
     * Remove multiple tags from the current user.
     *
     * @param keys The collection of keys, all of which will be removed from the current user.
     */
    fun removeTags(keys: Collection<String>)

    /**
     * Return a copy of all local tags from the current user.
     */
    fun getTags(): Map<String, String>

    /**
     * Add an observer to the user state, allowing the provider to be
     * notified whenever the user state has changed.
     *
     * Important: When using the observer to retrieve the onesignalId, check the externalId as well
     * to confirm the values are associated with the expected user.
     */
    fun addObserver(observer: IUserStateObserver)

    /**
     * Remove an observer from the user state.
     */
    fun removeObserver(observer: IUserStateObserver)
}
