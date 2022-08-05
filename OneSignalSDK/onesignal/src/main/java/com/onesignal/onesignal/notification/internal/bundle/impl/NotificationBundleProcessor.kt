package com.onesignal.onesignal.notification.internal.bundle.impl

import android.content.Context
import android.os.Bundle
import com.onesignal.onesignal.core.internal.common.JSONUtils
import com.onesignal.onesignal.core.internal.time.ITime
import com.onesignal.onesignal.notification.internal.bundle.INotificationBundleProcessor
import com.onesignal.onesignal.notification.internal.common.NotificationConstants
import com.onesignal.onesignal.notification.internal.common.NotificationFormatHelper
import com.onesignal.onesignal.notification.internal.generation.INotificationGenerationWorkManager
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class NotificationBundleProcessor(
    private val _workManager: INotificationGenerationWorkManager,
    private val _time: ITime,
) : INotificationBundleProcessor {

    // Format our short keys into more readable ones.
    private fun maximizeButtonsFromBundle(fcmBundle: Bundle) {
        if (!fcmBundle.containsKey("o")) return
        try {
            val customJSON = JSONObject(fcmBundle.getString("custom"))
            val additionalDataJSON: JSONObject
            additionalDataJSON =
                if (customJSON.has(PUSH_ADDITIONAL_DATA_KEY)) customJSON.getJSONObject(
                    PUSH_ADDITIONAL_DATA_KEY
                ) else JSONObject()
            val buttons = JSONArray(fcmBundle.getString(PUSH_MINIFIED_BUTTONS_LIST))
            fcmBundle.remove(PUSH_MINIFIED_BUTTONS_LIST)
            for (i in 0 until buttons.length()) {
                val button = buttons.getJSONObject(i)
                val buttonText = button.getString(PUSH_MINIFIED_BUTTON_TEXT)
                button.remove(PUSH_MINIFIED_BUTTON_TEXT)
                var buttonId: String?
                if (button.has(PUSH_MINIFIED_BUTTON_ID)) {
                    buttonId = button.getString(PUSH_MINIFIED_BUTTON_ID)
                    button.remove(PUSH_MINIFIED_BUTTON_ID)
                } else buttonId = buttonText
                button.put("id", buttonId)
                button.put("text", buttonText)
                if (button.has(PUSH_MINIFIED_BUTTON_ICON)) {
                    button.put("icon", button.getString(PUSH_MINIFIED_BUTTON_ICON))
                    button.remove(PUSH_MINIFIED_BUTTON_ICON)
                }
            }
            additionalDataJSON.put("actionButtons", buttons)
            additionalDataJSON.put(NotificationConstants.GENERATE_NOTIFICATION_BUNDLE_KEY_ACTION_ID, DEFAULT_ACTION)
            if (!customJSON.has(PUSH_ADDITIONAL_DATA_KEY)) customJSON.put(
                PUSH_ADDITIONAL_DATA_KEY,
                additionalDataJSON
            )
            fcmBundle.putString("custom", customJSON.toString())
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * Process bundle passed from FCM / HMS / ADM broadcast receiver
     */
    override fun processBundleFromReceiver(context: Context, bundle: Bundle, ) : INotificationBundleProcessor.ProcessedBundleResult? {

        val bundleResult = INotificationBundleProcessor.ProcessedBundleResult()

        // Not a OneSignal FCM message
        if (!NotificationFormatHelper.isOneSignalBundle(bundle)) {
            return bundleResult
        }

        bundleResult.setOneSignalPayload(true)
        maximizeButtonsFromBundle(bundle)

        val jsonPayload = JSONUtils.bundleAsJSONObject(bundle)
        val timestamp = _time.currentTimeMillis / 1000L
        val isRestoring = bundle.getBoolean("is_restoring", false)
        val isHighPriority = bundle.getString("pri", "0").toInt() > 9

        val osNotificationId = NotificationFormatHelper.getOSNotificationIdFromJson(jsonPayload)
        var androidNotificationId = 0
        if (bundle.containsKey(ANDROID_NOTIFICATION_ID)) androidNotificationId =
            bundle.getInt(
                ANDROID_NOTIFICATION_ID
            )

        val processed = _workManager.beginEnqueueingWork(
            context,
            osNotificationId!!,
            androidNotificationId,
            jsonPayload,
            timestamp,
            isRestoring,
            isHighPriority
        )

        bundleResult.isWorkManagerProcessing = processed

        return bundleResult;
    }

    companion object {
        const val PUSH_ADDITIONAL_DATA_KEY = "a"
        const val PUSH_MINIFIED_BUTTONS_LIST = "o"
        const val PUSH_MINIFIED_BUTTON_ID = "i"
        const val PUSH_MINIFIED_BUTTON_TEXT = "n"
        const val PUSH_MINIFIED_BUTTON_ICON = "p"
        private const val ANDROID_NOTIFICATION_ID = "android_notif_id"
        const val DEFAULT_ACTION = "__DEFAULT__"
    }
}