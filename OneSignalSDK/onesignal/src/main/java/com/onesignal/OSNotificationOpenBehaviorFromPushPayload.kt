package com.onesignal

import android.content.Context
import android.net.Uri
import org.json.JSONObject

class OSNotificationOpenBehaviorFromPushPayload(
    private val context: Context,
    private val fcmPayload: JSONObject,
) {

    val shouldOpenApp: Boolean
        get() {
            if (!OSNotificationOpenAppSettings.getOpenApp(context)) return false
            if (uri != null) return false
            return true
        }

    val uri: Uri?
        get() {
            if (OSNotificationOpenAppSettings.getSuppressLaunchURL(context)) return null

            val customJSON = JSONObject(fcmPayload.optString("custom"))

            if (customJSON.has("u")) {
                val url = customJSON.optString("u")
                if (url != "") {
                    return Uri.parse(url.trim { it <= ' ' })
                }
            }

            return null
        }

}