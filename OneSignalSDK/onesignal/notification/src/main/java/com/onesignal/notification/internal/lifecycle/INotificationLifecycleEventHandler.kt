package com.onesignal.notification.internal.lifecycle

import android.app.Activity
import com.onesignal.notification.internal.common.NotificationGenerationJob
import org.json.JSONArray

interface INotificationLifecycleEventHandler {
    /**
     * Called *after* the notification has been generated and processed by the SDK.
     */
    suspend fun onNotificationReceived(notificationJob: NotificationGenerationJob)
    suspend fun onNotificationOpened(activity: Activity, data: JSONArray, notificationId: String)
}
