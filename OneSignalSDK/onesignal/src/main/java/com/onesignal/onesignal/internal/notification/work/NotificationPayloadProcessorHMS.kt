package com.onesignal.onesignal.internal.notification.work

import com.onesignal.OSInAppMessagePreviewHandler.notificationOpened
import android.app.Activity
import android.content.Context
import android.content.Intent
import com.onesignal.onesignal.OneSignal
import com.onesignal.onesignal.internal.common.JSONUtils
import com.onesignal.onesignal.internal.notification.NotificationConstants
import com.onesignal.onesignal.internal.notification.NotificationFormatHelper
import com.onesignal.onesignal.internal.notification.NotificationHelper
import org.json.JSONException
import org.json.JSONObject

internal object NotificationPayloadProcessorHMS {
    fun handleHMSNotificationOpenIntent(activity: Activity, intent: Intent?) {
        OneSignal.initWithContext(activity.applicationContext)
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
            val actionId = custom.remove(NotificationConstants.GENERATE_NOTIFICATION_BUNDLE_KEY_ACTION_ID) as String
                ?: return
            jsonData.put(NotificationConstants.GENERATE_NOTIFICATION_BUNDLE_KEY_ACTION_ID, actionId)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun handleProcessJsonOpenData(activity: Activity, jsonData: JSONObject) {
        if (notificationOpened(activity, jsonData))
            return

        //TODO: Implement
//        OneSignal.handleNotificationOpen(
//            activity,
//            JSONArray().put(jsonData),
//            NotificationFormatHelper.getOSNotificationIdFromJson(jsonData)
//        )
    }

    // HMS notification with Message Type being Message won't trigger Activity reverse trampolining logic
    // for this case OneSignal rely on NotificationOpenedActivityHMS activity
    // Last EMUI (12 to the date) is based on Android 10, so no
    // Activity trampolining restriction exist for HMS devices
    fun processDataMessageReceived(context: Context, data: String?) {
        if (data == null)
            return

        val bundle = JSONUtils.jsonStringToBundle(data) ?: return

        val bundleProcessor = OneSignal.getService<INotificationBundleProcessor>()

        bundleProcessor.processBundleFromReceiver(context, bundle,)
    }
}