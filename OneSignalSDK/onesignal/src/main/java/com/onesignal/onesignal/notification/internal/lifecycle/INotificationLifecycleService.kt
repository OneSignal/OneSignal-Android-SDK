package com.onesignal.onesignal.notification.internal.lifecycle

import android.app.Activity
import com.onesignal.onesignal.core.internal.common.events.IEventProducer
import com.onesignal.onesignal.notification.internal.common.NotificationGenerationJob
import org.json.JSONArray

interface INotificationLifecycleService : IEventProducer<INotificationLifecycleEventHandler> {
    fun notificationReceived(notificationJob: NotificationGenerationJob)
    fun notificationOpened(activity: Activity, data: JSONArray, notificationId: String)
}