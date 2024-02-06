package com.onesignal.inAppMessages.internal.preview

import android.app.Activity
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.time.ITime
import com.onesignal.inAppMessages.internal.display.IInAppDisplayer
import com.onesignal.inAppMessages.internal.state.InAppStateService
import com.onesignal.notifications.internal.INotificationActivityOpener
import com.onesignal.notifications.internal.common.NotificationConstants
import com.onesignal.notifications.internal.common.NotificationGenerationJob
import com.onesignal.notifications.internal.common.NotificationHelper
import com.onesignal.notifications.internal.display.INotificationDisplayer
import com.onesignal.notifications.internal.lifecycle.INotificationLifecycleCallback
import com.onesignal.notifications.internal.lifecycle.INotificationLifecycleService
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class InAppMessagePreviewHandler(
    private val _iamDisplayer: IInAppDisplayer,
    private val _applicationService: IApplicationService,
    private val _notificationDisplayer: INotificationDisplayer,
    private val _notificationActivityOpener: INotificationActivityOpener,
    private val _notificationLifeCycle: INotificationLifecycleService,
    private val _state: InAppStateService,
    private val _time: ITime,
) : IStartableService, INotificationLifecycleCallback {
    override fun start() {
        _notificationLifeCycle.setInternalNotificationLifecycleCallback(this)
    }

    override suspend fun canReceiveNotification(jsonPayload: JSONObject): Boolean {
        // Show In-App message preview it is in the payload & the app is in focus
        val previewUUID = inAppPreviewPushUUID(jsonPayload) ?: return true

        // If app is in focus display the IAMs preview now
        if (_applicationService.isInForeground) {
            _state.inAppMessageIdShowing = previewUUID
            val result = _iamDisplayer.displayPreviewMessage(previewUUID)
            if (!result) {
                _state.inAppMessageIdShowing = null
            }
        } else if (shouldDisplayNotification()) {
            val generationJob = NotificationGenerationJob(jsonPayload, _time)
            _notificationDisplayer.displayNotification(generationJob)
        }

        return false
    }

    override suspend fun canOpenNotification(
        activity: Activity,
        jsonData: JSONObject,
    ): Boolean {
        val previewUUID = inAppPreviewPushUUID(jsonData) ?: return true

        _notificationActivityOpener.openDestinationActivity(activity, JSONArray().put(jsonData))

        _state.inAppMessageIdShowing = previewUUID
        val result = _iamDisplayer.displayPreviewMessage(previewUUID)
        if (!result) {
            _state.inAppMessageIdShowing = null
        }
        return false
    }

    private fun inAppPreviewPushUUID(payload: JSONObject): String? {
        val osCustom: JSONObject =
            try {
                NotificationHelper.getCustomJSONObject(payload)
            } catch (e: JSONException) {
                return null
            }

        if (!osCustom.has(NotificationConstants.PUSH_ADDITIONAL_DATA_KEY)) {
            return null
        }

        return osCustom.optJSONObject(NotificationConstants.PUSH_ADDITIONAL_DATA_KEY)?.let { additionalData ->
            if (additionalData.has(NotificationConstants.IAM_PREVIEW_KEY)) {
                additionalData.optString(NotificationConstants.IAM_PREVIEW_KEY)
            } else {
                null
            }
        }
    }

    // Validate that the current Android device is Android 4.4 or higher
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.KITKAT)
    private fun shouldDisplayNotification(): Boolean = Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2
}
