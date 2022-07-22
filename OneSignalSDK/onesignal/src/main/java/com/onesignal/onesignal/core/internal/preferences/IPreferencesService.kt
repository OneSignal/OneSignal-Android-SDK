package com.onesignal.onesignal.core.internal.preferences


interface IPreferencesService {
    fun getString(store: String, key: String, defValue: String? = null) : String?
    fun getBool(store: String, key: String, defValue: Boolean? = null) : Boolean?
    fun getInt(store: String, key: String, defValue: Int? = null) : Int?
    fun getLong(store: String, key: String, defValue: Long? = null) : Long?
    fun getStringSet(store: String, key: String, defValue: Set<String>? = null) : Set<String>?

    fun saveString(store: String, key: String, value: String?)
    fun saveBool(store: String, key: String, value: Boolean?)
    fun saveInt(store: String, key: String, value: Int?)
    fun saveLong(store: String, key: String, value: Long?)
    fun saveStringSet(store: String, key: String, value: Set<String>?)
}

object PreferenceStores {
    const val ONESIGNAL = "OneSignal"
    const val PLAYER_PURCHASES = "GTPlayerPurchases"
    const val TRIGGERS = "OneSignalTriggers"
}

object PreferencePlayerPurchasesKeys {
    // Player Purchase Keys
    const val PREFS_PURCHASE_TOKENS = "purchaseTokens"
    const val PREFS_EXISTING_PURCHASES = "ExistingPurchases"
}

object PreferenceTriggerKeys {

}

object PreferenceOneSignalKeys {
    // TODO: Remove this once the tasks below have been finished...
    //  1. Fix all of the SharedPreference Keys so they are organized by usage with comments
    //  ex.
    //   // In-App Messaging
    //   public static final String PREFS_OS_CACHED_IAMS = "PREFS_OS_CACHED_IAMS";
    //   public static final String PREFS_OS_DISMISSED_IAMS = "PREFS_OS_DISPLAYED_IAMS";
    //   public static final String PREFS_OS_IMPRESSIONED_IAMS = "PREFS_OS_IMPRESSIONED_IAMS";
    //   public static final String PREFS_OS_CLICKED_CLICK_IDS_IAMS = "PREFS_OS_CLICKED_CLICK_IDS_IAMS";
    //  2. Match keys with value names
    //  ex.
    //   public static final String PREFS_OS_LAST_LOCATION_TIME = "OS_LAST_LOCATION_TIME";
    //  3. Follow syntax and make new names relevant (specific and as short as possible)
    //  ex.
    //   Start with prefix "PREFS_OS_" + "LAST_LOCATION_TIME"
    // Unorganized Keys
    const val PREFS_OS_LAST_LOCATION_TIME = "OS_LAST_LOCATION_TIME"
    const val PREFS_GT_SOUND_ENABLED = "GT_SOUND_ENABLED"
    const val PREFS_OS_LAST_SESSION_TIME = "OS_LAST_SESSION_TIME"
    const val PREFS_GT_VIBRATE_ENABLED = "GT_VIBRATE_ENABLED"
    const val PREFS_OS_FILTER_OTHER_GCM_RECEIVERS = "OS_FILTER_OTHER_GCM_RECEIVERS"
    const val PREFS_GT_APP_ID = "GT_APP_ID"
    const val PREFS_GT_PLAYER_ID = "GT_PLAYER_ID"
    const val PREFS_GT_UNSENT_ACTIVE_TIME = "GT_UNSENT_ACTIVE_TIME"
    const val PREFS_OS_UNSENT_ATTRIBUTED_ACTIVE_TIME = "OS_UNSENT_ATTRIBUTED_ACTIVE_TIME"
    const val PREFS_ONESIGNAL_USERSTATE_DEPENDVALYES_ = "ONESIGNAL_USERSTATE_DEPENDVALYES_"
    const val PREFS_ONESIGNAL_USERSTATE_SYNCVALYES_ = "ONESIGNAL_USERSTATE_SYNCVALYES_"
    const val PREFS_ONESIGNAL_ACCEPTED_NOTIFICATION_LAST = "ONESIGNAL_ACCEPTED_NOTIFICATION_LAST"
    const val PREFS_ONESIGNAL_SUBSCRIPTION_LAST = "ONESIGNAL_SUBSCRIPTION_LAST"
    const val PREFS_ONESIGNAL_PLAYER_ID_LAST = "ONESIGNAL_PLAYER_ID_LAST"
    const val PREFS_ONESIGNAL_PUSH_TOKEN_LAST = "ONESIGNAL_PUSH_TOKEN_LAST"
    const val PREFS_ONESIGNAL_PERMISSION_ACCEPTED_LAST = "ONESIGNAL_PERMISSION_ACCEPTED_LAST"
    const val PREFS_GT_DO_NOT_SHOW_MISSING_GPS = "GT_DO_NOT_SHOW_MISSING_GPS"
    const val PREFS_ONESIGNAL_SUBSCRIPTION = "ONESIGNAL_SUBSCRIPTION"
    const val PREFS_ONESIGNAL_SYNCED_SUBSCRIPTION = "ONESIGNAL_SYNCED_SUBSCRIPTION"
    const val PREFS_GT_REGISTRATION_ID = "GT_REGISTRATION_ID"
    const val PREFS_ONESIGNAL_USER_PROVIDED_CONSENT = "ONESIGNAL_USER_PROVIDED_CONSENT"
    const val PREFS_OS_ETAG_PREFIX = "PREFS_OS_ETAG_PREFIX_"
    const val PREFS_OS_HTTP_CACHE_PREFIX = "PREFS_OS_HTTP_CACHE_PREFIX_"

    // Remote params
    const val PREFS_GT_FIREBASE_TRACKING_ENABLED = "GT_FIREBASE_TRACKING_ENABLED"
    const val PREFS_OS_RESTORE_TTL_FILTER = "OS_RESTORE_TTL_FILTER"
    const val PREFS_OS_CLEAR_GROUP_SUMMARY_CLICK = "OS_CLEAR_GROUP_SUMMARY_CLICK"
    const val PREFS_OS_UNSUBSCRIBE_WHEN_NOTIFICATIONS_DISABLED = "PREFS_OS_UNSUBSCRIBE_WHEN_NOTIFICATIONS_DISABLED"
    const val PREFS_OS_DISABLE_GMS_MISSING_PROMPT = "PREFS_OS_DISABLE_GMS_MISSING_PROMPT"
    const val PREFS_OS_REQUIRES_USER_PRIVACY_CONSENT = "PREFS_OS_REQUIRES_USER_PRIVACY_CONSENT"
    const val PREFS_OS_LOCATION_SHARED = "PREFS_OS_LOCATION_SHARED"

    // Remote params - Receive Receipts (aka Confirmed Deliveries)
    const val PREFS_OS_RECEIVE_RECEIPTS_ENABLED = "PREFS_OS_RECEIVE_RECEIPTS_ENABLED"

    // Remote params - Outcomes V2 service enabled
    const val PREFS_OS_OUTCOMES_V2 = "PREFS_OS_OUTCOMES_V2"

    // On Focus Influence
    const val PREFS_OS_ATTRIBUTED_INFLUENCES = "PREFS_OS_ATTRIBUTED_INFLUENCES"

    // Email
    const val PREFS_OS_EMAIL_ID = "OS_EMAIL_ID"
    const val PREFS_ONESIGNAL_EMAIL_ID_LAST = "PREFS_ONESIGNAL_EMAIL_ID_LAST"
    const val PREFS_ONESIGNAL_EMAIL_ADDRESS_LAST = "PREFS_ONESIGNAL_EMAIL_ADDRESS_LAST"

    // SMS
    const val PREFS_OS_SMS_ID = "PREFS_OS_SMS_ID"
    const val PREFS_OS_SMS_ID_LAST = "PREFS_OS_SMS_ID_LAST"
    const val PREFS_OS_SMS_NUMBER_LAST = "PREFS_OS_SMS_NUMBER_LAST"

    // In-App Messaging
    const val PREFS_OS_CACHED_IAMS = "PREFS_OS_CACHED_IAMS"
    const val PREFS_OS_DISMISSED_IAMS = "PREFS_OS_DISPLAYED_IAMS"
    const val PREFS_OS_IMPRESSIONED_IAMS = "PREFS_OS_IMPRESSIONED_IAMS"
    const val PREFS_OS_CLICKED_CLICK_IDS_IAMS = "PREFS_OS_CLICKED_CLICK_IDS_IAMS"
    const val PREFS_OS_PAGE_IMPRESSIONED_IAMS = "PREFS_OS_PAGE_IMPRESSIONED_IAMS"
    const val PREFS_OS_LAST_TIME_IAM_DISMISSED = "PREFS_OS_LAST_TIME_IAM_DISMISSED"
}