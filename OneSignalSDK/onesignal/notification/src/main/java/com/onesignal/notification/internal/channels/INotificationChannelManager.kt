package com.onesignal.notification.internal.channels

import com.onesignal.notification.internal.common.NotificationGenerationJob
import org.json.JSONArray

/**
 * Manager of the notification channels on the current device.
 */
internal interface INotificationChannelManager {

    /**
     * Create a notification channel, returning it's identifier to post notifications to.
     */
    fun createNotificationChannel(notificationJob: NotificationGenerationJob): String

    /**
     * Process the list of notification channels that have been configured for this app.
     */
    fun processChannelList(list: JSONArray?)
}
