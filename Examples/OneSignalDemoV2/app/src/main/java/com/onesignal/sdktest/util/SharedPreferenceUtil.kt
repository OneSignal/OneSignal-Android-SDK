package com.onesignal.sdktest.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

object SharedPreferenceUtil {

    private const val APP_SHARED_PREFS = "com.onesignal.sdktest"
    private const val OS_APP_ID_SHARED_PREF = "OS_APP_ID_SHARED_PREF"
    private const val PRIVACY_CONSENT_SHARED_PREF = "PRIVACY_CONSENT_SHARED_PREF"
    private const val USER_EXTERNAL_USER_ID_SHARED_PREF = "USER_EXTERNAL_USER_ID_SHARED_PREF"
    private const val LOCATION_SHARED_PREF = "LOCATION_SHARED_PREF"
    private const val IN_APP_MESSAGING_PAUSED_PREF = "IN_APP_MESSAGING_PAUSED_PREF"
    private const val TRIGGERS_PREF = "TRIGGERS_PREF"

    private fun getSharedPreference(context: Context): SharedPreferences {
        return context.getSharedPreferences(APP_SHARED_PREFS, Context.MODE_PRIVATE)
    }

    fun exists(context: Context, key: String): Boolean {
        return getSharedPreference(context).contains(key)
    }

    fun getOneSignalAppId(context: Context): String? {
        val defaultAppId = "77e32082-ea27-42e3-a898-c72e141824ef"
        return getSharedPreference(context).getString(OS_APP_ID_SHARED_PREF, defaultAppId)
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
        return getSharedPreference(context).getBoolean(IN_APP_MESSAGING_PAUSED_PREF, true)
    }

    fun getCachedTriggers(context: Context): Map<String, String> {
        val json = getSharedPreference(context).getString(TRIGGERS_PREF, null) ?: return emptyMap()
        return try {
            val jsonObject = JSONObject(json)
            val result = mutableMapOf<String, String>()
            jsonObject.keys().forEach { key ->
                result[key] = jsonObject.getString(key)
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun cacheOneSignalAppId(context: Context, appId: String) {
        getSharedPreference(context).edit().putString(OS_APP_ID_SHARED_PREF, appId).apply()
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

    fun cacheTriggers(context: Context, triggers: Map<String, String>) {
        val jsonObject = JSONObject()
        triggers.forEach { (key, value) ->
            jsonObject.put(key, value)
        }
        getSharedPreference(context).edit().putString(TRIGGERS_PREF, jsonObject.toString()).apply()
    }
}
