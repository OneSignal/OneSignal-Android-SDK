package com.onesignal.notification.internal.open.impl

import android.app.Activity
import android.content.Intent
import com.onesignal.core.internal.common.JSONUtils
import com.onesignal.notification.internal.common.NotificationConstants
import com.onesignal.notification.internal.common.NotificationFormatHelper
import com.onesignal.notification.internal.common.NotificationHelper
import com.onesignal.notification.internal.lifecycle.INotificationLifecycleService
import com.onesignal.notification.internal.open.INotificationOpenedProcessorHMS
import org.json.JSONException
import org.json.JSONObject

internal class NotificationOpenedProcessorHMS(
    private val _lifecycleService: INotificationLifecycleService
) : INotificationOpenedProcessorHMS {

    override suspend fun handleHMSNotificationOpenIntent(activity: Activity, intent: Intent?) {
        if (intent == null) return
        val jsonData = covertHMSOpenIntentToJson(
            intent
        )
            ?: return
        handleProcessJsonOpenData(activity, jsonData)
    }

    // Takes in a Notification Open Intent fired from HMS Core and coverts it to an OS formatted JSONObject
    // Returns null if it is NOT a notification sent from OneSignal's backend
    private fun covertHMSOpenIntentToJson(intent: Intent?): JSONObject? {
        // Validate Intent to prevent any side effects or crashes
        //    if triggered outside of OneSignal for any reason.
        if (!NotificationFormatHelper.isOneSignalIntent(intent)) return null
        val bundle = intent!!.extras
        val jsonData = JSONUtils.bundleAsJSONObject(bundle!!)
        reformatButtonClickAction(jsonData)
        return jsonData
    }

    // Un-nests JSON, key actionId, if it exists under custom
    // Example:
    //   From this:
    //      { custom: { actionId: "exampleId" } }
    //   To this:
    //      { custom: { }, actionId: "exampleId" } }
    private fun reformatButtonClickAction(jsonData: JSONObject) {
        try {
            val custom = NotificationHelper.getCustomJSONObject(jsonData)
            val actionId = custom.remove(NotificationConstants.GENERATE_NOTIFICATION_BUNDLE_KEY_ACTION_ID) as String? ?: return
            jsonData.put(NotificationConstants.GENERATE_NOTIFICATION_BUNDLE_KEY_ACTION_ID, actionId)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private suspend fun handleProcessJsonOpenData(activity: Activity, jsonData: JSONObject) {
        if (!_lifecycleService.canOpenNotification(activity, jsonData)) {
            return
        }

        _lifecycleService.notificationOpened(activity, JSONUtils.wrapInJsonArray(jsonData), NotificationFormatHelper.getOSNotificationIdFromJson(jsonData)!!)
    }
}
