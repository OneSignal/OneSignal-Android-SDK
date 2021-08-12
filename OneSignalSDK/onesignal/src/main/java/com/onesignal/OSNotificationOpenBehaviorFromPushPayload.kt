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
            return OSNotificationOpenAppSettings.getShouldOpenActivity(context)
                    && uri == null
        }

    val uri: Uri?
        get() {
            if (!OSNotificationOpenAppSettings.getShouldOpenActivity(context)) return null
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