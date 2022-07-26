package com.onesignal.onesignal.notification.internal.common

import android.net.Uri
import com.onesignal.onesignal.core.internal.common.time.ITime
import com.onesignal.onesignal.notification.internal.Notification
import org.json.JSONObject
import java.security.SecureRandom

class NotificationGenerationJob {
    var notification: Notification?
        get() = _notification
        set(value) {
            // If there is no android ID on the notification coming in, create one either
            // copying from the previous one or generating a new one.
            if (value != null && !value!!.hasNotificationId()) {
                val curNotification = _notification
                if (curNotification != null && curNotification.hasNotificationId())
                    value.androidNotificationId = curNotification.androidNotificationId
                else
                    value.androidNotificationId = SecureRandom().nextInt()
            }

            _notification = value
        }

    private var _notification: Notification? = null

    var jsonPayload: JSONObject? = null
    var isRestoring = false
    var isNotificationToDisplay = false
    var shownTimeStamp: Long? = null
    var overriddenBodyFromExtender: CharSequence? = null
    var overriddenTitleFromExtender: CharSequence? = null
    var overriddenSound: Uri? = null
    var overriddenFlags: Int? = null
    var orgFlags: Int? = null
    var orgSound: Uri? = null

    internal constructor() {
    }

    internal constructor(jsonPayload: JSONObject?, time: ITime) : this(
        Notification(jsonPayload!!, time), jsonPayload
    )

    internal constructor(notification: Notification, jsonPayload: JSONObject?) {
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

    fun hasExtender(): Boolean {
        return notification!!.notificationExtender != null
    }

    val apiNotificationId: String
        get() = NotificationHelper.getNotificationIdFromFCMJson(jsonPayload) ?: ""

    val androidId: Int
        get() = notification!!.androidNotificationId

    override fun toString(): String {
        return "NotificationGenerationJob{" +
                "jsonPayload=" + jsonPayload +
                ", isRestoring=" + isRestoring +
                ", isNotificationToDisplay=" + isNotificationToDisplay +
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