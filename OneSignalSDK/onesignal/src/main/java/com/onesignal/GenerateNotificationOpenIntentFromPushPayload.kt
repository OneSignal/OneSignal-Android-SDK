package com.onesignal

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject

/**
 * Create a GenerateNotificationOpenIntent instance based on:
 *    * OSNotificationOpenAppSettings
 *    * Payload
 */
object GenerateNotificationOpenIntentFromPushPayload {

    fun create(
        context: Context,
        fcmPayload: JSONObject
    ): GenerateNotificationOpenIntent {

        val payloadSpecificIntent = openBrowserIntent(
            context,
            fcmPayload
        )

        return GenerateNotificationOpenIntent(
            context,
            payloadSpecificIntent,
            OSNotificationOpenAppSettings.getDefaultAppOpenDisabled(context)
        )
    }

    private fun openBrowserIntent(
        context: Context,
        fcmPayload: JSONObject
    ): Intent? {
        if (OSNotificationOpenAppSettings.getSuppressLaunchURL(context)) return null

        val customJSON = JSONObject(fcmPayload.optString("custom"))

        if (customJSON.has("u")) {
            val url = customJSON.optString("u")
            if (url != "") {
                val uri = Uri.parse(url.trim { it <= ' ' })
                return OSUtils.openURLInBrowserIntent(uri)
            }
        }

        return null
    }

}