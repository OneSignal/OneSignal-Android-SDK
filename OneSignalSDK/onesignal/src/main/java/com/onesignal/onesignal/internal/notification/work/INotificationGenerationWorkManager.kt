package com.onesignal.onesignal.internal.notification.work

import android.content.Context
import org.json.JSONObject

interface INotificationGenerationWorkManager {
    fun beginEnqueueingWork(
        context: Context,
        osNotificationId: String,
        androidNotificationId: Int,
        jsonPayload: JSONObject?,
        timestamp: Long,
        isRestoring: Boolean,
        isHighPriority: Boolean
    ) : Boolean
}