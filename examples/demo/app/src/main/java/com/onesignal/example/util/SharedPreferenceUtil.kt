package com.onesignal.example.util

import android.content.Context
import android.content.SharedPreferences
import com.onesignal.example.data.model.NotificationExtensionOptions

object SharedPreferenceUtil {

    private const val APP_SHARED_PREFS = "com.onesignal.example"
    private const val PRIVACY_CONSENT_SHARED_PREF = "PRIVACY_CONSENT_SHARED_PREF"
    private const val USER_EXTERNAL_USER_ID_SHARED_PREF = "USER_EXTERNAL_USER_ID_SHARED_PREF"
    private const val LOCATION_SHARED_PREF = "LOCATION_SHARED_PREF"
    private const val IN_APP_MESSAGING_PAUSED_PREF = "IN_APP_MESSAGING_PAUSED_PREF"
    private const val CONSENT_REQUIRED_PREF = "CONSENT_REQUIRED_PREF"
    private const val IDENTITY_VERIFICATION_PREF = "IDENTITY_VERIFICATION_PREF"
    private const val JWT_TOKEN_PREF = "JWT_TOKEN_PREF"

    // Notification service extension switches. DemoNotificationServiceExtension reads these
    // directly because it runs whether or not the app is open.
    private const val NSE_ENABLED_PREF = "NSE_ENABLED_PREF"
    private const val NSE_LOG_DETAILS_PREF = "NSE_LOG_DETAILS_PREF"
    private const val NSE_APPLY_EXTENDER_PREF = "NSE_APPLY_EXTENDER_PREF"
    private const val NSE_FORCE_HIGH_IMPORTANCE_PREF = "NSE_FORCE_HIGH_IMPORTANCE_PREF"
    private const val NSE_DELAY_DISPLAY_PREF = "NSE_DELAY_DISPLAY_PREF"
    private const val NSE_DISCARD_PREF = "NSE_DISCARD_PREF"

    private fun getSharedPreference(context: Context): SharedPreferences {
        return context.getSharedPreferences(APP_SHARED_PREFS, Context.MODE_PRIVATE)
    }

    fun exists(context: Context, key: String): Boolean {
        return getSharedPreference(context).contains(key)
    }

    fun getUserPrivacyConsent(context: Context): Boolean {
        return getSharedPreference(context).getBoolean(PRIVACY_CONSENT_SHARED_PREF, false)
    }

    fun getCachedUserExternalUserId(context: Context): String {
        return getSharedPreference(context).getString(USER_EXTERNAL_USER_ID_SHARED_PREF, "") ?: ""
    }

    fun getCachedLocationSharedStatus(context: Context): Boolean {
        return getSharedPreference(context).getBoolean(LOCATION_SHARED_PREF, false)
    }

    fun getCachedInAppMessagingPausedStatus(context: Context): Boolean {
        // Default to NOT paused so a fresh install behaves like every other
        // demo (Capacitor / Cordova / RN / Flutter / etc): IAMs display until
        // the user explicitly toggles the pause switch on.
        return getSharedPreference(context).getBoolean(IN_APP_MESSAGING_PAUSED_PREF, false)
    }

    fun cacheUserPrivacyConsent(context: Context, privacyConsent: Boolean) {
        getSharedPreference(context).edit().putBoolean(PRIVACY_CONSENT_SHARED_PREF, privacyConsent).apply()
    }

    fun cacheUserExternalUserId(context: Context, userId: String) {
        getSharedPreference(context).edit().putString(USER_EXTERNAL_USER_ID_SHARED_PREF, userId).apply()
    }

    fun cacheLocationSharedStatus(context: Context, shared: Boolean) {
        getSharedPreference(context).edit().putBoolean(LOCATION_SHARED_PREF, shared).apply()
    }

    fun cacheInAppMessagingPausedStatus(context: Context, paused: Boolean) {
        getSharedPreference(context).edit().putBoolean(IN_APP_MESSAGING_PAUSED_PREF, paused).apply()
    }

    fun getCachedConsentRequired(context: Context): Boolean {
        return getSharedPreference(context).getBoolean(CONSENT_REQUIRED_PREF, false)
    }

    fun cacheConsentRequired(context: Context, required: Boolean) {
        getSharedPreference(context).edit().putBoolean(CONSENT_REQUIRED_PREF, required).apply()
    }

    fun getCachedIdentityVerification(context: Context): Boolean {
        return getSharedPreference(context).getBoolean(IDENTITY_VERIFICATION_PREF, false)
    }

    fun cacheIdentityVerification(context: Context, enabled: Boolean) {
        getSharedPreference(context).edit().putBoolean(IDENTITY_VERIFICATION_PREF, enabled).apply()
    }

    fun getCachedJwtToken(context: Context): String? {
        return getSharedPreference(context).getString(JWT_TOKEN_PREF, null)
    }

    fun cacheJwtToken(context: Context, token: String?) {
        getSharedPreference(context).edit().putString(JWT_TOKEN_PREF, token).apply()
    }

    // Every switch defaults to false so a fresh install behaves as if no extension were
    // registered. See NotificationExtensionOptions.
    fun getNotificationExtensionOptions(context: Context): NotificationExtensionOptions {
        val prefs = getSharedPreference(context)
        return NotificationExtensionOptions(
            enabled = prefs.getBoolean(NSE_ENABLED_PREF, false),
            logDetails = prefs.getBoolean(NSE_LOG_DETAILS_PREF, false),
            applyExtender = prefs.getBoolean(NSE_APPLY_EXTENDER_PREF, false),
            forceHighImportanceChannel = prefs.getBoolean(NSE_FORCE_HIGH_IMPORTANCE_PREF, false),
            delayDisplay = prefs.getBoolean(NSE_DELAY_DISPLAY_PREF, false),
            discard = prefs.getBoolean(NSE_DISCARD_PREF, false),
        )
    }

    fun cacheNotificationExtensionOptions(context: Context, options: NotificationExtensionOptions) {
        getSharedPreference(context).edit()
            .putBoolean(NSE_ENABLED_PREF, options.enabled)
            .putBoolean(NSE_LOG_DETAILS_PREF, options.logDetails)
            .putBoolean(NSE_APPLY_EXTENDER_PREF, options.applyExtender)
            .putBoolean(NSE_FORCE_HIGH_IMPORTANCE_PREF, options.forceHighImportanceChannel)
            .putBoolean(NSE_DELAY_DISPLAY_PREF, options.delayDisplay)
            .putBoolean(NSE_DISCARD_PREF, options.discard)
            .apply()
    }
}
