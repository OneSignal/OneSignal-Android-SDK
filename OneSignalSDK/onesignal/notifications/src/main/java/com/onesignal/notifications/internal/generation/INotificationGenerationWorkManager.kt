package com.onesignal.notifications.internal.generation

import android.content.Context
import org.json.JSONObject

internal interface INotificationGenerationWorkManager {
    fun beginEnqueueingWork(
        context: Context,
        osNotificationId: String,
        androidNotificationId: Int,
        jsonPayload: JSONObject?,
        timestamp: Long,
        isRestoring: Boolean,
        isHighPriority: Boolean,
    ): Boolean
}
