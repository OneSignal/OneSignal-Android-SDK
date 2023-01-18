package com.onesignal.notifications.internal

import com.onesignal.notifications.INotification
import com.onesignal.notifications.INotificationAction
import com.onesignal.notifications.INotificationClickHandler
import com.onesignal.notifications.INotificationClickResult
import org.json.JSONObject

/**
 * The data provided to [INotificationClickHandler.notificationClicked] when a notification
 * has been opened by the user.
 */
internal class NotificationClickResult(
    notif: Notification,
    actn: NotificationAction
) : INotificationClickResult {
    /** The notification that was opened by the user. **/
    override val notification: INotification
        get() = _notification

    /** The action the user took to open the notification. **/
    override val action: INotificationAction
        get() = _action

    private val _notification: Notification = notif
    private val _action: NotificationAction = actn

    fun toJSONObject(): JSONObject {
        return JSONObject()
            .put("notification", _notification.toJSONObject())
            .put("action", _action.toJSONObject())
    }
}
