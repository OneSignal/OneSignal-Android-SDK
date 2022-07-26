package com.onesignal.onesignal.notification.internal.lifecycle

import android.app.Activity
import com.onesignal.onesignal.notification.internal.common.NotificationGenerationJob
import org.json.JSONArray

interface INotificationLifecycleEventHandler {
    fun onReceived(notificationJob: NotificationGenerationJob)
    fun onOpened(activity: Activity, data: JSONArray, notificationId: String)
}
