package com.onesignal.onesignal.notification.internal.lifecycle.impl

import android.app.Activity
import com.onesignal.onesignal.core.internal.common.events.CallbackProducer
import com.onesignal.onesignal.core.internal.common.events.EventProducer
import com.onesignal.onesignal.notification.internal.common.NotificationGenerationJob
import com.onesignal.onesignal.notification.internal.lifecycle.INotificationLifecycleCallback
import com.onesignal.onesignal.notification.internal.lifecycle.INotificationLifecycleEventHandler
import com.onesignal.onesignal.notification.internal.lifecycle.INotificationLifecycleService
import org.json.JSONArray
import org.json.JSONObject

internal class NotificationLifecycleService() :
    EventProducer<INotificationLifecycleEventHandler>(),
    INotificationLifecycleService {

    private val _callback = CallbackProducer<INotificationLifecycleCallback>()

    override val hasCallback: Boolean
            get() = _callback.hasCallback

    override fun set(handler: INotificationLifecycleCallback?) = _callback.set(handler)

    override suspend fun canReceiveNotification(jsonPayload: JSONObject): Boolean {
        var canReceive = true
        _callback.suspendingFire { canReceive = it.canReceiveNotification(jsonPayload) }
        return canReceive
    }

    override suspend fun notificationGenerated(notificationJob: NotificationGenerationJob) {
        suspendingFire { it.onNotificationGenerated(notificationJob) }
    }

    override suspend fun canOpenNotification(activity: Activity, data: JSONObject): Boolean {
        var canOpen = false
        _callback.suspendingFire { canOpen = it.canOpenNotification(activity, data) }
        return canOpen
    }

    override suspend fun notificationOpened(activity: Activity, data: JSONArray, notificationId: String) {
        suspendingFire { it.onNotificationOpened(activity, data, notificationId) }
    }
}