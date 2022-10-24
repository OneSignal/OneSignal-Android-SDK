package com.onesignal.notification.internal.generation

import android.content.Context
import org.json.JSONObject

internal interface INotificationGenerationProcessor {
    suspend fun processNotificationData(
        context: Context,
        androidNotificationId: Int,
        jsonPayload: JSONObject,
        isRestoring: Boolean,
        timestamp: Long
    )
}
