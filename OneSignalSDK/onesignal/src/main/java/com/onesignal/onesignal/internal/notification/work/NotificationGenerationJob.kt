package com.onesignal.onesignal.internal.notification.work

import android.content.Context
import android.net.Uri
import com.onesignal.onesignal.internal.common.time.ITime
import com.onesignal.onesignal.internal.notification.Notification
import com.onesignal.onesignal.internal.notification.NotificationHelper
import org.json.JSONObject
import java.security.SecureRandom

class NotificationGenerationJob {
    var notification: Notification = Notification()
    var context: Context
    var jsonPayload: JSONObject? = null
    var isRestoring = false
    var shownTimeStamp: Long? = null
    var overriddenBodyFromExtender: CharSequence? = null
    var overriddenTitleFromExtender: CharSequence? = null
    var overriddenSound: Uri? = null
    var overriddenFlags: Int? = null
    var orgFlags: Int? = null
    var orgSound: Uri? = null

    internal constructor(context: Context) {
        this.context = context
    }

    internal constructor(context: Context, jsonPayload: JSONObject?, time: ITime) : this(
        context, Notification(jsonPayload!!, time), jsonPayload
    )

    internal constructor(context: Context, notification: Notification, jsonPayload: JSONObject?) {
        this.context = context
        this.jsonPayload = jsonPayload
        this.notification = notification
    }

    /**
     * Get the notification title from the payload
     */
    val title: CharSequence
        get() = overriddenTitleFromExtender ?: notification!!.title as CharSequence

    /**
     * Get the notification body from the payload
     */
    val body: CharSequence
        get() = overriddenBodyFromExtender ?: notification!!.body as CharSequence

    /**
     * Get the notification additional data json from the payload
     */
    val additionalData: JSONObject
        get() = notification!!.additionalData ?: JSONObject()

    /**
     * If androidNotificationId is -1 then the notification is a silent one
     */
    val isNotificationToDisplay: Boolean
        get() = androidIdWithoutCreate != -1

    fun hasExtender(): Boolean {
        return notification!!.notificationExtender != null
    }

    val apiNotificationId: String
        get() = NotificationHelper.getNotificationIdFromFCMJson(jsonPayload) ?: ""

    val androidIdWithoutCreate: Int
        get() = if (!notification!!.hasNotificationId()) -1 else notification!!.androidNotificationId

    val androidId: Int
        get() {
            if (!notification!!.hasNotificationId())
                notification!!.androidNotificationId = SecureRandom().nextInt()

            return notification!!.androidNotificationId
        }

    fun setAndroidIdWithoutOverriding(id: Int?) {
        if (id == null) return
        if (notification!!.hasNotificationId()) return
        notification!!.androidNotificationId = id
    }

    override fun toString(): String {
        return "OSNotificationGenerationJob{" +
                "jsonPayload=" + jsonPayload +
                ", isRestoring=" + isRestoring +
                ", shownTimeStamp=" + shownTimeStamp +
                ", overriddenBodyFromExtender=" + overriddenBodyFromExtender +
                ", overriddenTitleFromExtender=" + overriddenTitleFromExtender +
                ", overriddenSound=" + overriddenSound +
                ", overriddenFlags=" + overriddenFlags +
                ", orgFlags=" + orgFlags +
                ", orgSound=" + orgSound +
                ", notification=" + notification +
                '}'
    }
}