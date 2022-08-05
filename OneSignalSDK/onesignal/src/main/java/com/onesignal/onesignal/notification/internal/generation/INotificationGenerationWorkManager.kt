package com.onesignal.onesignal.notification.internal.generation

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