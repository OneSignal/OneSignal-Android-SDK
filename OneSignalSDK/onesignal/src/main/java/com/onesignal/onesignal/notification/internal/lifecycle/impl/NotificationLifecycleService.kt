package com.onesignal.onesignal.notification.internal.lifecycle.impl

import android.app.Activity
import com.onesignal.onesignal.core.internal.common.events.EventProducer
import com.onesignal.onesignal.notification.internal.common.NotificationGenerationJob
import com.onesignal.onesignal.notification.internal.lifecycle.INotificationLifecycleEventHandler
import com.onesignal.onesignal.notification.internal.lifecycle.INotificationLifecycleService
import org.json.JSONArray

internal class NotificationLifecycleService : EventProducer<INotificationLifecycleEventHandler>(),
    INotificationLifecycleService {

    override fun notificationReceived(notificationJob: NotificationGenerationJob) {
        fire { it.onReceived(notificationJob) }
    }

    override fun notificationOpened(activity: Activity, data: JSONArray, notificationId: String) {
        fire { it.onOpened(activity, data, notificationId) }
    }
}