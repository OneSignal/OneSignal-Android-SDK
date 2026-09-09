package com.onesignal.notifications.internal.generation

import android.content.Context
import com.onesignal.notifications.internal.common.NotificationRestoreReason
import org.json.JSONObject

internal interface INotificationGenerationWorkManager {
    fun beginEnqueueingWork(
        context: Context,
        osNotificationId: String,
        androidNotificationId: Int,
        jsonPayload: JSONObject?,
        timestamp: Long,
        restoreReason: NotificationRestoreReason?,
        isHighPriority: Boolean,
    ): Boolean
}
