package com.onesignal.notifications.internal.common

import android.net.Uri
import com.onesignal.core.internal.time.ITime
import com.onesignal.notifications.internal.Notification
import org.json.JSONObject
import java.security.SecureRandom

class NotificationGenerationJob(
    inNotification: Notification,
    var jsonPayload: JSONObject,
) {
    val notification: Notification = inNotification.setAndroidNotificationId()

    private fun Notification.setAndroidNotificationId() =
        this.also {
            // If there is no android ID on the notification coming in, generate a new one.
            if (it != null && !it.hasNotificationId()) {
                it.androidNotificationId = SecureRandom().nextInt()
            }
        }

    var isRestoring = false
    var isNotificationToDisplay = false
    var shownTimeStamp: Long? = null
    var overriddenBodyFromExtender: CharSequence? = null
    var overriddenTitleFromExtender: CharSequence? = null
    var overriddenSound: Uri? = null
    var overriddenFlags: Int? = null
    var overriddenColor: Int? = null
    var orgFlags: Int? = null
    var orgSound: Uri? = null
    var orgColor: Int? = null

    constructor(jsonPayload: JSONObject, time: ITime) : this(
        Notification(jsonPayload, time),
        jsonPayload,
    )

    /**
     * Get the notification title from the payload
     */
    val title: CharSequence?
        get() = overriddenTitleFromExtender ?: notification!!.title as CharSequence?

    /**
     * Get the notification body from the payload
     */
    val body: CharSequence?
        get() = overriddenBodyFromExtender ?: notification!!.body as CharSequence?

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
            ", overriddenColor=" + overriddenColor +
            ", orgFlags=" + orgFlags +
            ", orgSound=" + orgSound +
            ", orgColor=" + orgColor +
            ", notification=" + notification +
            '}'
    }
}
