package com.onesignal.notifications

import org.json.JSONObject

/**
 * The data provided to [INotificationClickListener.onClick] when a notification
 * has been clicked by the user.
 */
interface INotificationClickEvent {
    /**
     * The notification that was clicked by the user.
     */
    val notification: INotification

    /**
     * The result of the user clicking the notification.
     */
    val result: INotificationClickResult

    /**
     * The notification click event as JSON.
     */
    fun toJSONObject(): JSONObject
}
