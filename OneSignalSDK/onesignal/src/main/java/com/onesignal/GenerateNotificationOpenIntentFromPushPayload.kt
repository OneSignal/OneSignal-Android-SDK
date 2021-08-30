package com.onesignal

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject

/**
 * Create a GenerateNotificationOpenIntent instance based on:
 *    * OSNotificationOpenBehaviorFromPushPayload
 *    * Payload
 */
object GenerateNotificationOpenIntentFromPushPayload {
    fun create(
        context: Context,
        fcmPayload: JSONObject
    ): GenerateNotificationOpenIntent {
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
        val isIAMPreviewNotification = OSInAppMessagePreviewHandler.inAppPreviewPushUUID(fcmPayload) != null
        return isIAMPreviewNotification or
            shouldOpenApp
    }

    private fun openBrowserIntent(
        uri: Uri?,
    ): Intent? {
        if (uri == null) return null
        return OSUtils.openURLInBrowserIntent(uri)
    }
}