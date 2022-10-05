package com.onesignal.core.internal.influence.impl

import com.onesignal.core.internal.influence.InfluenceType
import com.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceStores
import org.json.JSONArray
import org.json.JSONException

/**
 * Setter and Getter of Notifications received
 */
internal class InfluenceDataRepository(
    private val preferences: IPreferencesService,
    private val _configModelStore: ConfigModelStore
) :
    IInfluenceDataRepository {
    /**
     * Cache a influence type enum for Notification as a string
     */
    override fun cacheNotificationInfluenceType(influenceType: InfluenceType) {
        preferences.saveString(
            PreferenceStores.ONESIGNAL,
            InfluenceConstants.PREFS_OS_OUTCOMES_CURRENT_NOTIFICATION_INFLUENCE,
            influenceType.toString()
        )
    }

    /**
     * Get the current cached influence type string, convert it to the influence type enum, and return it
     */
    override val notificationCachedInfluenceType: InfluenceType
        get() {
            val influenceType = preferences.getString(
                PreferenceStores.ONESIGNAL,
                InfluenceConstants.PREFS_OS_OUTCOMES_CURRENT_NOTIFICATION_INFLUENCE,
                InfluenceType.UNATTRIBUTED.toString()
            )
            return InfluenceType.fromString(influenceType)
        }

    /**
     * Cache a influence type enum for IAM as a string
     */
    override fun cacheIAMInfluenceType(influenceType: InfluenceType) {
        preferences.saveString(
            PreferenceStores.ONESIGNAL,
            InfluenceConstants.PREFS_OS_OUTCOMES_CURRENT_IAM_INFLUENCE,
            influenceType.toString()
        )
    }

    /**
     * Get the current cached influence type string, convert it to the influence type enum, and return it
     */
    override val iamCachedInfluenceType: InfluenceType
        get() {
            val defaultValue = InfluenceType.UNATTRIBUTED.toString()
            val influenceType = preferences.getString(
                PreferenceStores.ONESIGNAL,
                InfluenceConstants.PREFS_OS_OUTCOMES_CURRENT_IAM_INFLUENCE,
                defaultValue
            )
            return InfluenceType.fromString(influenceType)
        }

    /**
     * Cache attributed notification opened
     */
    override fun cacheNotificationOpenId(id: String?) {
        preferences.saveString(
            PreferenceStores.ONESIGNAL,
            InfluenceConstants.PREFS_OS_LAST_ATTRIBUTED_NOTIFICATION_OPEN,
            id
        )
    }

    /**
     * Get the current cached notification id, null if not direct
     */
    override val cachedNotificationOpenId: String?
        get() = preferences.getString(
            PreferenceStores.ONESIGNAL,
            InfluenceConstants.PREFS_OS_LAST_ATTRIBUTED_NOTIFICATION_OPEN,
            null
        )

    override fun saveNotifications(notifications: JSONArray) {
        preferences.saveString(
            PreferenceStores.ONESIGNAL,
            InfluenceConstants.PREFS_OS_LAST_NOTIFICATIONS_RECEIVED,
            notifications.toString()
        )
    }

    override fun saveIAMs(iams: JSONArray) {
        preferences.saveString(
            PreferenceStores.ONESIGNAL,
            InfluenceConstants.PREFS_OS_LAST_IAMS_RECEIVED,
            iams.toString()
        )
    }

    @get:Throws(JSONException::class)
    override val lastNotificationsReceivedData: JSONArray
        get() {
            val notificationsReceived = preferences.getString(
                PreferenceStores.ONESIGNAL,
                InfluenceConstants.PREFS_OS_LAST_NOTIFICATIONS_RECEIVED,
                "[]"
            )
            return notificationsReceived?.let { JSONArray(it) } ?: JSONArray()
        }

    @get:Throws(JSONException::class)
    override val lastIAMsReceivedData: JSONArray
        get() {
            val iamReceived = preferences.getString(
                PreferenceStores.ONESIGNAL,
                InfluenceConstants.PREFS_OS_LAST_IAMS_RECEIVED,
                "[]"
            )
            return iamReceived?.let { JSONArray(it) } ?: JSONArray()
        }

    override val notificationLimit: Int
        get() = _configModelStore.get().influenceParams.notificationLimit

    override val iamLimit: Int
        get() = _configModelStore.get().influenceParams.iamLimit

    override val notificationIndirectAttributionWindow: Int
        get() = _configModelStore.get().influenceParams.indirectNotificationAttributionWindow

    override val iamIndirectAttributionWindow: Int
        get() = _configModelStore.get().influenceParams.indirectIAMAttributionWindow

    override val isDirectInfluenceEnabled: Boolean
        get() = _configModelStore.get().influenceParams.isDirectEnabled

    override val isIndirectInfluenceEnabled: Boolean
        get() = _configModelStore.get().influenceParams.isIndirectEnabled

    override val isUnattributedInfluenceEnabled: Boolean
        get() = _configModelStore.get().influenceParams.isUnattributedEnabled
}
