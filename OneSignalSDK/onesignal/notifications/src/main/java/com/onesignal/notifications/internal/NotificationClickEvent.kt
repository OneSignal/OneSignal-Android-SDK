package com.onesignal.notifications.internal

import com.onesignal.notifications.INotification
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationClickResult
import org.json.JSONObject

/**
 * The data provided to [INotificationClickListener.onClick] when a notification
 * has been opened by the user.
 */
internal class NotificationClickEvent(
    private val _notification: Notification,
    private val _result: NotificationClickResult,
) : INotificationClickEvent {
    /** The notification that was opened by the user. **/
    override val notification: INotification
        get() = _notification

    /** The action the user took to open the notification. **/
    override val result: INotificationClickResult
        get() = _result

    fun toJSONObject(): JSONObject {
        return JSONObject()
            .put("notification", _notification.toJSONObject())
            .put("action", _result.toJSONObject())
    }
}
