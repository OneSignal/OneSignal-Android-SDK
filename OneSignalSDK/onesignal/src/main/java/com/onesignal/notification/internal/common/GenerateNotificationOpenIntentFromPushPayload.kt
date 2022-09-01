package com.onesignal.notification.internal.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.onesignal.core.internal.common.AndroidUtils
import org.json.JSONObject

/**
 * Create a GenerateNotificationOpenIntent instance based on:
 *    * OSNotificationOpenBehaviorFromPushPayload
 *    * Payload
 */
internal object GenerateNotificationOpenIntentFromPushPayload {

    /**
     * Create a new [GenerateNotificationOpenIntent] from the FCM payload
     */
    fun create(context: Context, fcmPayload: JSONObject): GenerateNotificationOpenIntent {
        val behavior = OSNotificationOpenBehaviorFromPushPayload(
            context,
            fcmPayload,
        )

        return GenerateNotificationOpenIntent(
            context,
            openBrowserIntent(behavior.uri),
            behavior.shouldOpenApp
        )
    }

    private fun openBrowserIntent(
        uri: Uri?,
    ): Intent? {
        if (uri == null) return null
        return AndroidUtils.openURLInBrowserIntent(uri)
    }
}
