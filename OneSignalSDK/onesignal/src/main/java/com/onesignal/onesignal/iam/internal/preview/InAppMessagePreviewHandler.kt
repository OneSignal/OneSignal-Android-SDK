package com.onesignal.onesignal.iam.internal.preview

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.annotation.ChecksSdkIntAtLeast
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.common.JSONUtils
import com.onesignal.onesignal.core.internal.common.time.ITime
import com.onesignal.onesignal.iam.internal.IAMManager
import com.onesignal.onesignal.notification.internal.NotificationsManager
import com.onesignal.onesignal.notification.internal.common.NotificationConstants
import com.onesignal.onesignal.notification.internal.common.NotificationGenerationJob
import com.onesignal.onesignal.notification.internal.common.NotificationHelper
import com.onesignal.onesignal.notification.internal.display.INotificationDisplayer
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class InAppMessagePreviewHandler(
    private val _iamManager: IAMManager,
    private val _applicationService: IApplicationService,
    private val _notificationDisplayer: INotificationDisplayer,
    private val _notificationManager: NotificationsManager,
    private val _time: ITime
) {

    fun notificationReceived(bundle: Bundle): Boolean {
        val pushPayloadJson = JSONUtils.bundleAsJSONObject(bundle)
        // Show In-App message preview it is in the payload & the app is in focus
        val previewUUID = inAppPreviewPushUUID(pushPayloadJson) ?: return false

        // TODO: Address blocking
        runBlocking {
            // If app is in focus display the IAMs preview now
            if (_applicationService.isInForeground) {
                _iamManager.displayPreviewMessage(previewUUID)
            } else if (shouldDisplayNotification()) {
                val generationJob = NotificationGenerationJob(pushPayloadJson, _time)
                _notificationDisplayer.displayNotification(generationJob)
            }
        }
        return true
    }

    fun notificationOpened(activity: Activity, jsonData: JSONObject): Boolean {
        val previewUUID = inAppPreviewPushUUID(jsonData) ?: return false

        _notificationManager.openDestinationActivity(activity, JSONArray().put(jsonData))

        // TODO: Address blocking
        runBlocking {
            _iamManager.displayPreviewMessage(previewUUID)
        }

        return true
    }

    private fun inAppPreviewPushUUID(payload: JSONObject): String? {
        val osCustom: JSONObject = try {
            NotificationHelper.getCustomJSONObject(payload)
        } catch (e: JSONException) {
            return null
        }

        if (!osCustom.has(NotificationConstants.PUSH_ADDITIONAL_DATA_KEY))
            return null

        return osCustom.optJSONObject(NotificationConstants.PUSH_ADDITIONAL_DATA_KEY)?.let { additionalData ->
            if (additionalData.has(NotificationConstants.IAM_PREVIEW_KEY))
                additionalData.optString(NotificationConstants.IAM_PREVIEW_KEY)
            else
                null
        }
    }

    // Validate that the current Android device is Android 4.4 or higher
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.KITKAT)
    private fun shouldDisplayNotification(): Boolean = Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2
}
