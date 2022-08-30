package com.onesignal

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.annotation.ChecksSdkIntAtLeast
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal object OSInAppMessagePreviewHandler {
    @JvmStatic
    fun notificationReceived(context: Context?, bundle: Bundle?): Boolean {
        val pushPayloadJson = NotificationBundleProcessor.bundleAsJSONObject(bundle)
        // Show In-App message preview it is in the payload & the app is in focus
        val previewUUID = inAppPreviewPushUUID(pushPayloadJson) ?: return false

        // If app is in focus display the IAMs preview now
        if (OneSignal.isAppActive()) {
            OneSignal.getInAppMessageController().displayPreviewMessage(previewUUID)
        } else if (shouldDisplayNotification()) {
            val generationJob = OSNotificationGenerationJob(context, pushPayloadJson)
            GenerateNotification.displayIAMPreviewNotification(generationJob)
        }
        return true
    }

    @JvmStatic
    fun notificationOpened(activity: Activity, jsonData: JSONObject): Boolean {
        val previewUUID = inAppPreviewPushUUID(jsonData) ?: return false

        OneSignal.openDestinationActivity(activity, JSONArray().put(jsonData))
        OneSignal.getInAppMessageController().displayPreviewMessage(previewUUID)
        return true
    }

    @JvmStatic
    fun inAppPreviewPushUUID(payload: JSONObject): String? {
        val osCustom: JSONObject = try {
            NotificationBundleProcessor.getCustomJSONObject(payload)
        } catch (e: JSONException) {
            return null
        }

        if (!osCustom.has(NotificationBundleProcessor.PUSH_ADDITIONAL_DATA_KEY)) {
            return null
        }

        return osCustom.optJSONObject(NotificationBundleProcessor.PUSH_ADDITIONAL_DATA_KEY)?.let { additionalData ->
            if (additionalData.has(NotificationBundleProcessor.IAM_PREVIEW_KEY)) {
                additionalData.optString(NotificationBundleProcessor.IAM_PREVIEW_KEY)
            } else {
                null
            }
        }
    }

    // Validate that the current Android device is Android 4.4 or higher
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.KITKAT)
    private fun shouldDisplayNotification(): Boolean = Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2
}
