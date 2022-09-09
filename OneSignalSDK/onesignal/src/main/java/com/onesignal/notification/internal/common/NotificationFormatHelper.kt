package com.onesignal.notification.internal.common

import android.content.Intent
import android.os.Bundle
import com.onesignal.core.internal.logging.Logging
import org.json.JSONException
import org.json.JSONObject

// Current: All helpers are for parsing a push payload
// Future: This class could also support parsing our SDK generated bundles
internal object NotificationFormatHelper {
    const val PAYLOAD_OS_ROOT_CUSTOM = "custom"
    const val PAYLOAD_OS_NOTIFICATION_ID = "i"

    fun isOneSignalIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val bundle = intent.extras
        return isOneSignalBundle(bundle)
    }

    fun isOneSignalBundle(bundle: Bundle?): Boolean {
        return getOSNotificationIdFromBundle(bundle) != null
    }

    private fun getOSNotificationIdFromBundle(bundle: Bundle?): String? {
        if (bundle == null || bundle.isEmpty) return null
        val custom = bundle.getString(PAYLOAD_OS_ROOT_CUSTOM, null)

        if (custom != null) {
            return getOSNotificationIdFromJsonString(custom)
        }

        Logging.debug("Not a OneSignal formatted Bundle. No 'custom' field in the bundle.")
        return null
    }

    fun getOSNotificationIdFromJson(jsonObject: JSONObject?): String? {
        if (jsonObject == null) return null
        val custom = jsonObject.optString(PAYLOAD_OS_ROOT_CUSTOM, null)

        return getOSNotificationIdFromJsonString(custom)
    }

    private fun getOSNotificationIdFromJsonString(jsonStr: String?): String? {
        try {
            val customJSON = JSONObject(jsonStr)
            if (customJSON.has(PAYLOAD_OS_NOTIFICATION_ID)) {
                return customJSON.optString(PAYLOAD_OS_NOTIFICATION_ID, null)
            } else {
                Logging.debug("Not a OneSignal formatted JSON string. No 'i' field in custom.")
            }
        } catch (e: JSONException) {
            Logging.debug("Not a OneSignal formatted JSON String, error parsing string as JSON.")
        }
        return null
    }
}
