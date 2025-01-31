package com.onesignal.core.internal.preferences

/**
 * Provides access to the low level preferences.  There are one or more preference
 * stores, identified by [PreferenceStores], each store contains a key for each
 * preference.  Each key has a known data type, it's value can be fetched/stored as
 * needed.  Stored preferences will persist across the lifetime of the app installation.
 */
interface IPreferencesService {
    /**
     * Retrieve a [String] value identified by the [store] and [key] provided.
     *
     * @param store The name of the preference store.
     * @param key The key to retrieve.
     * @param defValue The optional default value to return, if the [key] was not previously saved.
     *
     * @return the value in the preference store, or [defValue] if not previously saved.
     */
    fun getString(
        store: String,
        key: String,
        defValue: String? = null,
    ): String?

    /**
     * Retrieve a [Boolean] value identified by the [store] and [key] provided.
     *
     * @param store The name of the preference store.
     * @param key The key to retrieve.
     * @param defValue The optional default value to return, if the [key] was not previously saved.
     *
     * @return the value in the preference store, or [defValue] if not previously saved.
     */
    fun getBool(
        store: String,
        key: String,
        defValue: Boolean? = null,
    ): Boolean?

    /**
     * Retrieve a [Int] value identified by the [store] and [key] provided.
     *
     * @param store The name of the preference store.
     * @param key The key to retrieve.
     * @param defValue The optional default value to return, if the [key] was not previously saved.
     *
     * @return the value in the preference store, or [defValue] if not previously saved.
     */
    fun getInt(
        store: String,
        key: String,
        defValue: Int? = null,
    ): Int?

    /**
     * Retrieve a [Long] value identified by the [store] and [key] provided.
     *
     * @param store The name of the preference store.
     * @param key The key to retrieve.
     * @param defValue The optional default value to return, if the [key] was not previously saved.
     *
     * @return the value in the preference store, or [defValue] if not previously saved.
     */
    fun getLong(
        store: String,
        key: String,
        defValue: Long? = null,
    ): Long?

    /**
     * Retrieve a [Set] of [String] value identified by the [store] and [key] provided.
     *
     * @param store The name of the preference store.
     * @param key The key to retrieve.
     * @param defValue The optional default value to return, if the [key] was not previously saved.
     *
     * @return the value in the preference store, or [defValue] if not previously saved.
     */
    fun getStringSet(
        store: String,
        key: String,
        defValue: Set<String>? = null,
    ): Set<String>?

    /**
     * Save a [String] value identified by the [store] and [key] provided.
     *
     * @param store The name of the preference store.
     * @param key The key to retrieve.
     * @param value The value to save.
     */
    fun saveString(
        store: String,
        key: String,
        value: String?,
    )

    /**
     * Save a [Boolean] value identified by the [store] and [key] provided.
     *
     * @param store The name of the preference store.
     * @param key The key to retrieve.
     * @param value The value to save.
     */
    fun saveBool(
        store: String,
        key: String,
        value: Boolean?,
    )

    /**
     * Save a [Int] value identified by the [store] and [key] provided.
     *
     * @param store The name of the preference store.
     * @param key The key to retrieve.
     * @param value The value to save.
     */
    fun saveInt(
        store: String,
        key: String,
        value: Int?,
    )

    /**
     * Save a [Long] value identified by the [store] and [key] provided.
     *
     * @param store The name of the preference store.
     * @param key The key to retrieve.
     * @param value The value to save.
     */
    fun saveLong(
        store: String,
        key: String,
        value: Long?,
    )

    /**
     * Save a [Set] of [String] value identified by the [store] and [key] provided.
     *
     * @param store The name of the preference store.
     * @param key The key to retrieve.
     * @param value The value to save.
     */
    fun saveStringSet(
        store: String,
        key: String,
        value: Set<String>?,
    )
}

object PreferenceStores {
    /**
     * The default OneSignal store, keys defined in [PreferenceOneSignalKeys].
     */
    const val ONESIGNAL = "OneSignal"

    /**
     * The player purchase store, keys defined in [PreferencePlayerPurchasesKeys].
     */
    const val PLAYER_PURCHASES = "GTPlayerPurchases"
}

object PreferencePlayerPurchasesKeys {
    // Player Purchase Keys

    /**
     * (String) The purchase tokens that have been tracked.
     */
    const val PREFS_PURCHASE_TOKENS = "purchaseTokens"

    /**
     * (Boolean) Whether new purchases should be treated as existing.
     */
    const val PREFS_EXISTING_PURCHASES = "ExistingPurchases"
}

object PreferenceOneSignalKeys {
    // Legacy

    /**
     * (String) The legacy app ID from SDKs prior to 5.
     */
    const val PREFS_LEGACY_APP_ID = "GT_APP_ID"

    /**
     * (String) The legacy player ID from SDKs prior to 5.
     */
    const val PREFS_LEGACY_PLAYER_ID = "GT_PLAYER_ID"

    /**
     * (String) The legacy player sync values from SDKS prior to 5.
     */
    const val PREFS_LEGACY_USER_SYNCVALUES = "ONESIGNAL_USERSTATE_SYNCVALYES_CURRENT_STATE"

    // Location

    /**
     * (Long) The last time the device location was captured, in Unix time milliseconds.
     */
    const val PREFS_OS_LAST_LOCATION_TIME = "OS_LAST_LOCATION_TIME"

    /**
     * (Boolean) Whether location should be shared with OneSignal.
     */
    const val PREFS_OS_LOCATION_SHARED = "OS_LOCATION_SHARED"

    // Permissions

    /**
     * (Boolean) A prefix key for the permission state. When true, the user has rejected this
     * permission too many times and will not be prompted again.
     */
    const val PREFS_OS_USER_RESOLVED_PERMISSION_PREFIX = "USER_RESOLVED_PERMISSION_"

    // HTTP

    /**
     * (String) A prefix key for retrieving the ETAG for a given HTTP GET cache key. The cache
     * key should be appended to this prefix.
     */
    const val PREFS_OS_ETAG_PREFIX = "PREFS_OS_ETAG_PREFIX_"

    /**
     * (String) A install id, a UUIDv4 generated once when app is first opened.
     * Value is for a HTTP header, OneSignal-Install-Id, added on all calls
     * made to OneSignal's backend.
     */
    const val PREFS_OS_INSTALL_ID = "PREFS_OS_INSTALL_ID"

    /**
     * (String) A prefix key for retrieving the response for a given HTTP GET cache key. The cache
     * key should be appended to this prefix.
     */
    const val PREFS_OS_HTTP_CACHE_PREFIX = "PREFS_OS_HTTP_CACHE_PREFIX_"

    // Outcomes

    /**
     * (String Set) The set of unattributed outcome events that have occurred to ensure uniqueness when requested.
     */
    const val PREFS_OS_UNATTRIBUTED_UNIQUE_OUTCOME_EVENTS_SENT = "PREFS_OS_UNATTRIBUTED_UNIQUE_OUTCOME_EVENTS_SENT"

    // In-App Messaging

    /**
     * (String) The serialized IAMs TODO: This isn't currently used, determine if actually needed for cold start IAM fetch delay
     */
    const val PREFS_OS_CACHED_IAMS = "PREFS_OS_CACHED_IAMS"

    /**
     * (String Set) The set of IAM IDs that have been dismissed on this device.
     */
    const val PREFS_OS_DISMISSED_IAMS = "PREFS_OS_DISPLAYED_IAMS"

    /**
     * (String Set) The set of IAM IDs that have impressed (displayed) on the device.
     */
    const val PREFS_OS_IMPRESSIONED_IAMS = "PREFS_OS_IMPRESSIONED_IAMS"

    /**
     * (String Set) The set of click IDs that the device has clicked on.
     */
    const val PREFS_OS_CLICKED_CLICK_IDS_IAMS = "PREFS_OS_CLICKED_CLICK_IDS_IAMS"

    /**
     * (String Set) The set of page IDs that have impressed (displayed) on the device.
     */
    const val PREFS_OS_PAGE_IMPRESSIONED_IAMS = "PREFS_OS_PAGE_IMPRESSIONED_IAMS"

    /**
     * (Long) The last time an IAM was dismissed, in unix time milliseconds.
     */
    const val PREFS_OS_IAM_LAST_DISMISSED_TIME = "PREFS_OS_IAM_LAST_DISMISSED_TIME"

    // Models

    /**
     * (String) A prefix key for retrieving a specific model store contents.  The name of the model
     * store should be appended to this prefix.
     */
    const val MODEL_STORE_PREFIX = "MODEL_STORE_"
}
