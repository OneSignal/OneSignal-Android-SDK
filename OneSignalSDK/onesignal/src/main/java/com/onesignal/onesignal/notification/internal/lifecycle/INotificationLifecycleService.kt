package com.onesignal.onesignal.notification.internal.lifecycle

import android.app.Activity
import android.os.Bundle
import com.onesignal.onesignal.core.internal.common.events.ICallbackNotifier
import com.onesignal.onesignal.core.internal.common.events.IEventProducer
import com.onesignal.onesignal.notification.internal.common.NotificationGenerationJob
import org.json.JSONArray
import org.json.JSONObject

interface INotificationLifecycleService :
    IEventProducer<INotificationLifecycleEventHandler>,
    ICallbackNotifier<INotificationLifecycleCallback> {

    suspend fun canReceiveNotification(jsonPayload: JSONObject) : Boolean
    suspend fun notificationGenerated(notificationJob: NotificationGenerationJob)

    suspend fun canOpenNotification(activity: Activity, data: JSONObject) : Boolean
    suspend fun notificationOpened(activity: Activity, data: JSONArray, notificationId: String)
}