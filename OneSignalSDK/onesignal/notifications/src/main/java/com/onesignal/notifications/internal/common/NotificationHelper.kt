package com.onesignal.notifications.internal.common

import android.app.Notification
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.onesignal.common.AndroidUtils
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.INotification
import com.onesignal.notifications.internal.NotificationClickEvent
import com.onesignal.notifications.internal.NotificationClickResult
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.min

object NotificationHelper {
    const val GROUPLESS_SUMMARY_KEY = "os_group_undefined"
    const val GROUPLESS_SUMMARY_ID = -718463522

    /**
     * Getter for obtaining all active notifications
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    fun getActiveNotifications(context: Context): Array<StatusBarNotification> {
        var statusBarNotifications = arrayOf<StatusBarNotification>()
        try {
            statusBarNotifications = getNotificationManager(context).activeNotifications
        } catch (e: Throwable) {
            // try-catch for Android 6.0.X and possibly 8.0.0 bug work around,
            //    getActiveNotifications sometimes throws a fatal exception.
            // Seem to be related to what Android's internal method getAppActiveNotifications returns.
            // Issue #422
        }
        return statusBarNotifications
    }

    /**
     * Iterate over all active notifications and count the groupless ones
     * and return the int count
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    fun getGrouplessNotifsCount(context: Context): Int {
        val statusBarNotifications = getActiveNotifications(context)
        var groupCount = 0
        for (statusBarNotification in statusBarNotifications) {
            if (!NotificationCompat.isGroupSummary(statusBarNotification.notification) &&
                GROUPLESS_SUMMARY_KEY == statusBarNotification.notification.group
            ) {
                groupCount++
            }
        }
        return groupCount
    }

    /**
     * Getter for obtaining any groupless notifications
     * A groupless notification is:
     * 1. Not a summary
     * 2. A null group key or group key using assigned GROUPLESS_SUMMARY_KEY
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    fun getActiveGrouplessNotifications(context: Context): ArrayList<StatusBarNotification> {
        val grouplessStatusBarNotifications = ArrayList<StatusBarNotification>()

        /* Iterate over all active notifications and add the groupless non-summary
         * notifications to a ArrayList to be returned */
        val statusBarNotifications = getActiveNotifications(context)
        for (statusBarNotification in statusBarNotifications) {
            val notification = statusBarNotification.notification
            val isGroupSummary = isGroupSummary(statusBarNotification)
            val isGroupless = (
                notification.group == null ||
                    notification.group == GROUPLESS_SUMMARY_KEY
            )
            if (!isGroupSummary && isGroupless) {
                grouplessStatusBarNotifications.add(
                    statusBarNotification,
                )
            }
        }
        return grouplessStatusBarNotifications
    }

    fun isGroupSummary(notif: StatusBarNotification): Boolean {
        return notif.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
    }

    /**
     * All groupless notifications are assigned the GROUPLESS_SUMMARY_KEY and notify() is called
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun assignGrouplessNotifications(
        context: Context?,
        grouplessNotifs: ArrayList<StatusBarNotification>,
    ) {
        for (grouplessNotif in grouplessNotifs) {
            val grouplessNotifBuilder =
                Notification.Builder.recoverBuilder(context, grouplessNotif.notification)

            // Recreate the notification but with the groupless key instead
            val notif =
                grouplessNotifBuilder
                    .setGroup(GROUPLESS_SUMMARY_KEY)
                    .setOnlyAlertOnce(true)
                    .build()
            NotificationManagerCompat.from(context!!).notify(grouplessNotif.id, notif)
        }
    }

    /**
     * Determine whether notifications are enabled/disabled either for the entire app or for
     * a specific channel within the application.  Note, channels are only applicable beyond
     * API 26.
     *
     * @param context The app context to check notification enablement against.
     * @param channelId The optional channel ID to check enablement for.
     */
    fun areNotificationsEnabled(
        context: Context,
        channelId: String? = null,
    ): Boolean {
        try {
            val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            if (!notificationsEnabled) {
                return false
            }

            // Channels were introduced in O
            if (channelId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = getNotificationManager(context)?.getNotificationChannel(channelId)
                return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
            }
        } catch (t: Throwable) {
        }
        return true
    }

    fun getNotificationManager(context: Context): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Throws(JSONException::class)
    fun getCustomJSONObject(jsonObject: JSONObject): JSONObject {
        return JSONObject(jsonObject.optString("custom"))
    }

    fun getNotificationIdFromFCMJson(fcmJson: JSONObject?): String? {
        if (fcmJson == null) return null
        try {
            val customJSON = JSONObject(fcmJson.getString("custom"))
            if (customJSON.has("i")) {
                return customJSON.optString(
                    "i",
                    null,
                )
            } else {
                Logging.debug("Not a OneSignal formatted FCM message. No 'i' field in custom.")
            }
        } catch (e: JSONException) {
            Logging.debug("Not a OneSignal formatted FCM message. No 'custom' field in the JSONObject.")
        }
        return null
    }

    fun parseVibrationPattern(fcmBundle: JSONObject): LongArray? {
        try {
            val patternObj = fcmBundle.opt("vib_pt")
            val jsonVibArray: JSONArray
            jsonVibArray =
                if (patternObj is String) JSONArray(patternObj) else patternObj as JSONArray
            val longArray = LongArray(jsonVibArray.length())
            for (i in 0 until jsonVibArray.length()) longArray[i] = jsonVibArray.optLong(i)
            return longArray
        } catch (e: JSONException) {
        }
        return null
    }

    fun getSoundUri(
        context: Context,
        sound: String?,
    ): Uri? {
        val resources = context.resources
        val packageName = context.packageName
        var soundId: Int
        if (AndroidUtils.isValidResourceName(sound)) {
            soundId = resources.getIdentifier(sound, "raw", packageName)
            if (soundId != 0) return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + soundId)
        }
        soundId = resources.getIdentifier("onesignal_default_sound", "raw", packageName)
        return if (soundId != 0) Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + soundId) else null
    }

    fun getCampaignNameFromNotification(notification: INotification): String {
        if (notification.templateName?.isEmpty() != true && notification.templateId?.isEmpty() != true) {
            return notification.templateName + " - " + notification.templateId
        } else if (notification.title != null) {
            return notification.title!!.substring(0, min(10, notification.title!!.length))
        }
        return ""
    }

    internal fun generateNotificationOpenedResult(
        jsonArray: JSONArray,
        time: ITime,
    ): NotificationClickEvent {
        val jsonArraySize = jsonArray.length()
        var firstMessage = true
        val androidNotificationId =
            jsonArray.optJSONObject(0)
                .optInt(NotificationConstants.BUNDLE_KEY_ANDROID_NOTIFICATION_ID)
        val groupedNotifications: MutableList<com.onesignal.notifications.internal.Notification> = ArrayList()
        var actionSelected: String? = null
        var payload: JSONObject? = null

        for (i in 0 until jsonArraySize) {
            try {
                payload = jsonArray.getJSONObject(i)
                if (actionSelected == null && payload.has(NotificationConstants.GENERATE_NOTIFICATION_BUNDLE_KEY_ACTION_ID)) {
                    actionSelected = payload.optString(NotificationConstants.GENERATE_NOTIFICATION_BUNDLE_KEY_ACTION_ID, null)
                }

                if (firstMessage) {
                    firstMessage = false
                } else {
                    groupedNotifications.add(
                        com.onesignal.notifications.internal.Notification(
                            payload,
                            time,
                        ),
                    )
                }
            } catch (t: Throwable) {
                Logging.error("Error parsing JSON item $i/$jsonArraySize for callback.", t)
            }
        }

        val notification =
            com.onesignal.notifications.internal.Notification(
                groupedNotifications,
                payload!!,
                androidNotificationId,
                time,
            )

        val notificationResult = NotificationClickResult(actionSelected, notification.launchURL)

        return NotificationClickEvent(notification, notificationResult)
    }
}
