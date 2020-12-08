package com.onesignal.influence.data

import com.onesignal.OSSharedPreferences
import com.onesignal.OneSignalRemoteParams
import com.onesignal.OneSignalRemoteParams.InfluenceParams
import com.onesignal.influence.OSInfluenceConstants
import com.onesignal.influence.domain.OSInfluenceType
import com.onesignal.influence.domain.OSInfluenceType.Companion.fromString
import org.json.JSONArray
import org.json.JSONException

/**
 * Setter and Getter of Notifications received
 */
class OSInfluenceDataRepository(private val preferences: OSSharedPreferences) {
    /**
     * Cache a influence type enum for Notification as a string
     */
    fun cacheNotificationInfluenceType(influenceType: OSInfluenceType) {
        preferences.saveString(
                preferences.preferencesName,
                OSInfluenceConstants.PREFS_OS_OUTCOMES_CURRENT_NOTIFICATION_INFLUENCE,
                influenceType.toString()
        )
    }

    /**
     * Get the current cached influence type string, convert it to the influence type enum, and return it
     */
    val notificationCachedInfluenceType: OSInfluenceType
        get() {
            val influenceType = preferences.getString(
                    preferences.preferencesName,
                    OSInfluenceConstants.PREFS_OS_OUTCOMES_CURRENT_NOTIFICATION_INFLUENCE,
                    OSInfluenceType.UNATTRIBUTED.toString()
            )
            return fromString(influenceType)
        }

    /**
     * Cache a influence type enum for IAM as a string
     */
    fun cacheIAMInfluenceType(influenceType: OSInfluenceType) {
        preferences.saveString(
                preferences.preferencesName,
                OSInfluenceConstants.PREFS_OS_OUTCOMES_CURRENT_IAM_INFLUENCE,
                influenceType.toString()
        )
    }

    /**
     * Get the current cached influence type string, convert it to the influence type enum, and return it
     */
    val iamCachedInfluenceType: OSInfluenceType
        get() {
            val defaultValue = OSInfluenceType.UNATTRIBUTED.toString();
            val influenceType = preferences.getString(
                    preferences.preferencesName,
                    OSInfluenceConstants.PREFS_OS_OUTCOMES_CURRENT_IAM_INFLUENCE,
                    defaultValue
            )
            return fromString(influenceType)
        }

    /**
     * Cache attributed notification opened
     */
    fun cacheNotificationOpenId(id: String?) {
        preferences.saveString(
                preferences.preferencesName,
                OSInfluenceConstants.PREFS_OS_LAST_ATTRIBUTED_NOTIFICATION_OPEN,
                id
        )
    }

    /**
     * Get the current cached notification id, null if not direct
     */
    val cachedNotificationOpenId: String?
        get() = preferences.getString(
                preferences.preferencesName,
                OSInfluenceConstants.PREFS_OS_LAST_ATTRIBUTED_NOTIFICATION_OPEN,
                null
        )

    fun saveNotifications(notifications: JSONArray) {
        preferences.saveString(
                preferences.preferencesName,
                OSInfluenceConstants.PREFS_OS_LAST_NOTIFICATIONS_RECEIVED,
                notifications.toString())
    }

    fun saveIAMs(iams: JSONArray) {
        preferences.saveString(
                preferences.preferencesName,
                OSInfluenceConstants.PREFS_OS_LAST_IAMS_RECEIVED,
                iams.toString())
    }

    @get:Throws(JSONException::class)
    val lastNotificationsReceivedData: JSONArray
        get() {
            val notificationsReceived = preferences.getString(
                    preferences.preferencesName,
                    OSInfluenceConstants.PREFS_OS_LAST_NOTIFICATIONS_RECEIVED,
                    "[]")
            return notificationsReceived?.let { JSONArray(it) } ?: JSONArray()
        }

    @get:Throws(JSONException::class)
    val lastIAMsReceivedData: JSONArray
        get() {
            val iamReceived = preferences.getString(
                    preferences.preferencesName,
                    OSInfluenceConstants.PREFS_OS_LAST_IAMS_RECEIVED,
                    "[]")
            return iamReceived?.let { JSONArray(it) } ?: JSONArray()
        }

    val notificationLimit: Int
        get() = preferences.getInt(
                preferences.preferencesName,
                OSInfluenceConstants.PREFS_OS_NOTIFICATION_LIMIT,
                OneSignalRemoteParams.DEFAULT_NOTIFICATION_LIMIT
        )

    val iamLimit: Int
        get() = preferences.getInt(
                preferences.preferencesName,
                OSInfluenceConstants.PREFS_OS_IAM_LIMIT,
                OneSignalRemoteParams.DEFAULT_NOTIFICATION_LIMIT
        )

    val notificationIndirectAttributionWindow: Int
        get() = preferences.getInt(
                preferences.preferencesName,
                OSInfluenceConstants.PREFS_OS_NOTIFICATION_INDIRECT_ATTRIBUTION_WINDOW,
                OneSignalRemoteParams.DEFAULT_INDIRECT_ATTRIBUTION_WINDOW
        )

    val iamIndirectAttributionWindow: Int
        get() = preferences.getInt(
                preferences.preferencesName,
                OSInfluenceConstants.PREFS_OS_IAM_INDIRECT_ATTRIBUTION_WINDOW,
                OneSignalRemoteParams.DEFAULT_INDIRECT_ATTRIBUTION_WINDOW
        )

    val isDirectInfluenceEnabled: Boolean
        get() = preferences.getBool(
                preferences.preferencesName,
                OSInfluenceConstants.PREFS_OS_DIRECT_ENABLED,
                false
        )

    val isIndirectInfluenceEnabled: Boolean
        get() = preferences.getBool(
                preferences.preferencesName,
                OSInfluenceConstants.PREFS_OS_INDIRECT_ENABLED,
                false
        )

    val isUnattributedInfluenceEnabled: Boolean
        get() = preferences.getBool(
                preferences.preferencesName,
                OSInfluenceConstants.PREFS_OS_UNATTRIBUTED_ENABLED,
                false
        )

    fun saveInfluenceParams(influenceParams: InfluenceParams) {
        preferences.saveBool(
                preferences.preferencesName,
                OSInfluenceConstants.PREFS_OS_DIRECT_ENABLED,
                influenceParams.isDirectEnabled
        )
        preferences.saveBool(
                preferences.preferencesName,
                OSInfluenceConstants.PREFS_OS_INDIRECT_ENABLED,
                influenceParams.isIndirectEnabled
        )
        preferences.saveBool(
                preferences.preferencesName,
                OSInfluenceConstants.PREFS_OS_UNATTRIBUTED_ENABLED,
                influenceParams.isUnattributedEnabled
        )
        preferences.saveInt(
                preferences.preferencesName,
                OSInfluenceConstants.PREFS_OS_NOTIFICATION_LIMIT,
                influenceParams.notificationLimit
        )
        preferences.saveInt(
                preferences.preferencesName,
                OSInfluenceConstants.PREFS_OS_NOTIFICATION_INDIRECT_ATTRIBUTION_WINDOW,
                influenceParams.indirectNotificationAttributionWindow
        )
        preferences.saveInt(
                preferences.preferencesName,
                OSInfluenceConstants.PREFS_OS_IAM_LIMIT,
                influenceParams.iamLimit
        )
        preferences.saveInt(
                preferences.preferencesName,
                OSInfluenceConstants.PREFS_OS_IAM_INDIRECT_ATTRIBUTION_WINDOW,
                influenceParams.indirectIAMAttributionWindow
        )
    }
}