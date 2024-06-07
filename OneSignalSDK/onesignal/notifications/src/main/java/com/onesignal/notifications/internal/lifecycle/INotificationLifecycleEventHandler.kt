package com.onesignal.notifications.internal.lifecycle

import android.app.Activity
import com.onesignal.notifications.internal.common.NotificationGenerationJob
import org.json.JSONArray

interface INotificationLifecycleEventHandler {
    /**
     * Called *after* the notification has been generated and processed by the SDK.
     */
    suspend fun onNotificationReceived(notificationJob: NotificationGenerationJob)

    suspend fun onNotificationOpened(
        activity: Activity,
        data: JSONArray,
    )
}
