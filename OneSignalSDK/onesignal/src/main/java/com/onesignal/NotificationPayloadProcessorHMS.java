package com.onesignal;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.onesignal.GenerateNotification.BUNDLE_KEY_ACTION_ID;

class NotificationPayloadProcessorHMS {

    static void handleHmsNotificationOpenIntent(@NonNull Activity activity, @Nullable Intent intent) {
        OneSignal.setAppContext(activity);
        if (intent == null)
            return;

        JSONObject jsonData = covertHmsOpenIntentToJson(intent);
        if (jsonData == null)
            return;

        handleProcessJsonOpenData(activity, jsonData);
    }

    // Takes in a Notification Open Intent fired from HMS Core and coverts it to an OS formatted JSONObject
    // Returns null if it is NOT a notification sent from OneSignal's backend
    private static @Nullable JSONObject covertHmsOpenIntentToJson(@Nullable Intent intent) {
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
            false,
            OSNotificationFormatHelper.getOSNotificationIdFromJson(jsonData)
        );
    }
}
