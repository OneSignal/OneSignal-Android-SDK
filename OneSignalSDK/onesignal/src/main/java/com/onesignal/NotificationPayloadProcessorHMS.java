package com.onesignal;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.onesignal.GenerateNotification.BUNDLE_KEY_ACTION_ID;

class NotificationPayloadProcessorHMS {

    static void handleHMSNotificationOpenIntent(@NonNull Activity activity, @Nullable Intent intent) {
        OneSignal.initWithContext(activity.getApplicationContext());
        if (intent == null)
            return;

        JSONObject jsonData = covertHMSOpenIntentToJson(intent);
        if (jsonData == null)
            return;

        handleProcessJsonOpenData(activity, jsonData);
    }

    // Takes in a Notification Open Intent fired from HMS Core and coverts it to an OS formatted JSONObject
    // Returns null if it is NOT a notification sent from OneSignal's backend
    private static @Nullable JSONObject covertHMSOpenIntentToJson(@Nullable Intent intent) {
        // Validate Intent to prevent any side effects or crashes
        //    if triggered outside of OneSignal for any reason.
        if (!OSNotificationFormatHelper.isOneSignalIntent(intent))
            return null;

        Bundle bundle = intent.getExtras();
        JSONObject jsonData = NotificationBundleProcessor.bundleAsJSONObject(bundle);
        reformatButtonClickAction(jsonData);

        return jsonData;
    }

    // Un-nests JSON, key actionId, if it exists under custom
    // Example:
    //   From this:
    //      { custom: { actionId: "exampleId" } }
    //   To this:
    //      { custom: { }, actionId: "exampleId" } }
    private static void reformatButtonClickAction(@NonNull JSONObject jsonData) {
        try {
            JSONObject custom = NotificationBundleProcessor.getCustomJSONObject(jsonData);
            String actionId = (String)custom.remove(BUNDLE_KEY_ACTION_ID);
            if (actionId == null)
                return;

            jsonData.put(BUNDLE_KEY_ACTION_ID, actionId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private static void handleProcessJsonOpenData(@NonNull Activity activity, @NonNull JSONObject jsonData) {
        if (NotificationOpenedProcessor.handleIAMPreviewOpen(activity, jsonData))
            return;

        OneSignal.handleNotificationOpen(
            activity,
            new JSONArray().put(jsonData),
            true,
            OSNotificationFormatHelper.getOSNotificationIdFromJson(jsonData)
        );
    }

    // HMS notification with Message Type being Message won't trigger Activity reverse trampolining logic
    // for this case OneSignal rely on NotificationOpenedActivityHMS activity
    // Last EMUI (12 to the date) is based on Android 10, so no
    // Activity trampolining restriction exist for HMS devices
    public static void processDataMessageReceived(@NonNull final Context context, @Nullable String data) {
        OneSignal.initWithContext(context);
        if (data == null)
            return;

        final Bundle bundle = OSUtils.jsonStringToBundle(data);
        if (bundle == null)
            return;

        NotificationBundleProcessor.ProcessBundleReceiverCallback bundleReceiverCallback = new NotificationBundleProcessor.ProcessBundleReceiverCallback() {

            @Override
            public void onBundleProcessed(@Nullable NotificationBundleProcessor.ProcessedBundleResult processedResult) {
                // TODO: Figure out the correct replacement or usage of completeWakefulIntent method
                //      FCMBroadcastReceiver.completeWakefulIntent(intent);

                // Return if the notification will NOT be handled by normal GcmIntentService display flow.
                if (processedResult != null && processedResult.processed())
                    return;

                // TODO: 4.0.0 or after - What is in GcmBroadcastReceiver should be split into a shared class to support FCM, HMS, and ADM
                FCMBroadcastReceiver.startFCMService(context, bundle);
            }
        };
        NotificationBundleProcessor.processBundleFromReceiver(context, bundle, bundleReceiverCallback);

    }
}
