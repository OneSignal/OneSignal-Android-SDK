package com.onesignal.notifications.internal.generation

import android.content.Context
import com.onesignal.notifications.internal.common.NotificationRestoreReason
import org.json.JSONObject

internal interface INotificationGenerationProcessor {
    suspend fun processNotificationData(
        context: Context,
        androidNotificationId: Int,
        jsonPayload: JSONObject,
        restoreReason: NotificationRestoreReason?,
        timestamp: Long,
    )
}
