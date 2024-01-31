package com.onesignal.onesignal.notification.internal.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.onesignal.onesignal.core.internal.common.AndroidUtils
import org.json.JSONObject

/**
 * Create a GenerateNotificationOpenIntent instance based on:
 *    * OSNotificationOpenBehaviorFromPushPayload
 *    * Payload
 */
object GenerateNotificationOpenIntentFromPushPayload {

    /**
     * Create a new [GenerateNotificationOpenIntent] from the FCM payload
     */
    fun create(context: Context, fcmPayload: JSONObject) : GenerateNotificationOpenIntent {
        val behavior = OSNotificationOpenBehaviorFromPushPayload(
            context,
            fcmPayload,
        )

        return GenerateNotificationOpenIntent(
            context,
            openBrowserIntent(behavior.uri),
            shouldOpenApp(behavior.shouldOpenApp, fcmPayload)
        )
    }

    private fun shouldOpenApp(shouldOpenApp: Boolean, fcmPayload: JSONObject): Boolean {
        return true
        // TODO: Implement
//        val isIAMPreviewNotification = OSInAppMessagePreviewHandler.inAppPreviewPushUUID(fcmPayload) != null
//        return isIAMPreviewNotification or
//            shouldOpenApp
    }

    private fun openBrowserIntent(
        uri: Uri?,
    ): Intent? {
        if (uri == null) return null
        return AndroidUtils.openURLInBrowserIntent(uri)
    }
}