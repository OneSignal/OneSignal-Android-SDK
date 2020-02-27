package com.onesignal

import android.os.Build
import com.onesignal.OneSignalRemoteParams.OutcomesParams
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Setter and Getter of Notifications received
 */
internal object OutcomesUtils {
    const val NOTIFICATIONS_IDS = "notification_ids"
    const val NOTIFICATION_ID = "notification_id"
    const val TIME = "time"

    /**
     * Cache a session enum as a string
     */
    @JvmStatic
    fun cacheCurrentSession(session: OSSessionManager.Session) {
        OneSignalPrefs.saveString(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_OUTCOMES_CURRENT_SESSION,
                session.toString()
        )
    }

    /**
     * Get the current cached session string, convert it to the session enum, and return it
     */
    @JvmStatic
    val cachedSession
        get() = OneSignalPrefs.getString(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_OUTCOMES_CURRENT_SESSION,
                OSSessionManager.Session.UNATTRIBUTED.toString()
        ).run {
            OSSessionManager.Session.fromString(this)
        }

    /**
     * Cache attributed notification opened
     */
    @JvmStatic
    fun cacheNotificationOpenId(id: String?) {
        OneSignalPrefs.saveString(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_LAST_ATTRIBUTED_NOTIFICATION_OPEN,
                id
        )
    }

    /**
     * Get the current cached notification id, null if not direct
     */
    @JvmStatic
    val cachedNotificationOpenId: String?
        get() = OneSignalPrefs.getString(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_LAST_ATTRIBUTED_NOTIFICATION_OPEN,
                null
        )

    /**
     * Save state of last notification received
     */
    @JvmStatic
    fun markLastNotificationReceived(notificationId: String?) {
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Notification markLastNotificationReceived with id: $notificationId")
        if (notificationId == null || notificationId.isEmpty()) return

        var notificationsReceived: JSONArray? = null
        try {
            val lastNotificationReceived = OneSignalPrefs.getString(
                    OneSignalPrefs.PREFS_ONESIGNAL,
                    OneSignalPrefs.PREFS_OS_LAST_NOTIFICATIONS_RECEIVED,
                    "[]")
            notificationsReceived = JSONArray(lastNotificationReceived)
        } catch (e: JSONException) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating direct notification arrived:JSON Failed.", e)
        }

        notificationsReceived?.put(
                JSONObject()
                        .put(NOTIFICATION_ID, notificationId)
                        .put(TIME, System.currentTimeMillis())
        )?.also {
            val lengthDifference = it.length() - notificationLimit
            // Only save the last notifications ids without surpassing the limit
            // Always keep the max quantity of ids possible
            // If the attribution window increases, old notifications ids might influence the session
            if (lengthDifference > 0) {
                val notificationsToSave = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    for (i in 0 until lengthDifference) {
                        it.remove(i)
                    }
                    it
                } else {
                    JSONArray().apply {
                        for (i in lengthDifference until it.length()) {
                            this.put(it[i])
                        }
                    }
                }

                OneSignalPrefs.saveString(OneSignalPrefs.PREFS_ONESIGNAL, OneSignalPrefs.PREFS_OS_LAST_NOTIFICATIONS_RECEIVED, notificationsToSave.toString())
            }
        }
    }

    @JvmStatic
    val lastNotificationsReceivedData: JSONArray
        get() {
            OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
                    OneSignalPrefs.PREFS_OS_LAST_NOTIFICATIONS_RECEIVED,
                    "[]"
            ).run {
                return try {
                    JSONArray(this)
                } catch (e: JSONException) {
                    OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating last notifications received data:JSON Failed.", e)
                    JSONArray()
                }
            }
        }

    @JvmStatic
    val notificationLimit
        get() = OneSignalPrefs.getInt(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_NOTIFICATION_LIMIT,
                OneSignalRemoteParams.DEFAULT_NOTIFICATION_LIMIT
        )

    @JvmStatic
    val indirectAttributionWindow
        get() = OneSignalPrefs.getInt(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_INDIRECT_ATTRIBUTION_WINDOW,
                OneSignalRemoteParams.DEFAULT_INDIRECT_ATTRIBUTION_WINDOW
        )

    @JvmStatic
    val isDirectSessionEnabled
        get() = OneSignalPrefs.getBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_DIRECT_ENABLED,
                false
        )

    @JvmStatic
    val isIndirectSessionEnabled
        get() = OneSignalPrefs.getBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_INDIRECT_ENABLED,
                false
        )

    @JvmStatic
    val isUnattributedSessionEnabled
        get() = OneSignalPrefs.getBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_UNATTRIBUTED_ENABLED,
                false
        )

    @JvmStatic
    fun saveOutcomesParams(outcomesParams: OutcomesParams) {
        OneSignalPrefs.saveBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_DIRECT_ENABLED,
                outcomesParams.directEnabled
        )
        OneSignalPrefs.saveBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_INDIRECT_ENABLED,
                outcomesParams.indirectEnabled
        )
        OneSignalPrefs.saveBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_UNATTRIBUTED_ENABLED,
                outcomesParams.unattributedEnabled
        )
        OneSignalPrefs.saveInt(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_NOTIFICATION_LIMIT,
                outcomesParams.notificationLimit
        )
        OneSignalPrefs.saveInt(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_INDIRECT_ATTRIBUTION_WINDOW,
                outcomesParams.indirectAttributionWindow
        )
    }

}