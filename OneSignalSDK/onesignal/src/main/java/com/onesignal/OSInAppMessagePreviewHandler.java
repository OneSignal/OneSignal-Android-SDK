package com.onesignal;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import static com.onesignal.NotificationBundleProcessor.IAM_PREVIEW_KEY;
import static com.onesignal.NotificationBundleProcessor.PUSH_ADDITIONAL_DATA_KEY;

class OSInAppMessagePreviewHandler {

    static boolean inAppMessagePreviewHandled(Context context, Bundle bundle) {
        JSONObject pushPayloadJson = NotificationBundleProcessor.bundleAsJSONObject(bundle);
        // Show In-App message preview it is in the payload & the app is in focus
        String previewUUID = inAppPreviewPushUUID(pushPayloadJson);
        if (previewUUID != null) {
            // If app is in focus display the IAMs preview now
            if (OneSignal.isAppActive()) {
                OneSignal.getInAppMessageController().displayPreviewMessage(previewUUID);
            } else {
                OSNotificationGenerationJob generationJob = new OSNotificationGenerationJob(context, pushPayloadJson);
                GenerateNotification.displayIAMPreviewNotification(generationJob);
            }
            return true;
        }

        return false;
    }

    @Nullable
    static String inAppPreviewPushUUID(JSONObject payload) {
        JSONObject osCustom;
        try {
            osCustom = NotificationBundleProcessor.getCustomJSONObject(payload);
        } catch (JSONException e) {
            return null;
        }

        if (!osCustom.has(PUSH_ADDITIONAL_DATA_KEY))
            return null;

        JSONObject additionalData = osCustom.optJSONObject(PUSH_ADDITIONAL_DATA_KEY);
        if (additionalData.has(IAM_PREVIEW_KEY))
            return additionalData.optString(IAM_PREVIEW_KEY);
        return null;
    }
}
