package com.onesignal;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

// Current: All helpers are for parsing a push payload
// Future: This class could also support parsing our SDK generated bundles
class OSNotificationFormatHelper {

    public static final String PAYLOAD_OS_ROOT_CUSTOM = "custom";
    public static final String PAYLOAD_OS_NOTIFICATION_ID = "i";

    static boolean isOneSignalIntent(@Nullable Intent intent) {
        if (intent == null)
            return false;

        Bundle bundle = intent.getExtras();
        return OSNotificationFormatHelper.isOneSignalBundle(bundle);
    }

    static boolean isOneSignalBundle(@Nullable Bundle bundle) {
        return getOSNotificationIdFromBundle(bundle) != null;
    }

    @Nullable
    private static String getOSNotificationIdFromBundle(@Nullable Bundle bundle) {
        if (bundle == null || bundle.isEmpty())
            return null;

        String custom = bundle.getString(PAYLOAD_OS_ROOT_CUSTOM, null);
        if (custom != null)
            return getOSNotificationIdFromJsonString(custom);

        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Not a OneSignal formatted Bundle. No 'custom' field in the bundle.");
        return null;
    }

    @Nullable
    static String getOSNotificationIdFromJson(@Nullable JSONObject jsonObject) {
        if (jsonObject == null)
            return null;

        String custom = jsonObject.optString(PAYLOAD_OS_ROOT_CUSTOM, null);
        return getOSNotificationIdFromJsonString(custom);
    }

    @Nullable
    private static String getOSNotificationIdFromJsonString(@Nullable String jsonStr) {
        try {
            JSONObject customJSON = new JSONObject(jsonStr);
            if (customJSON.has(PAYLOAD_OS_NOTIFICATION_ID))
                return customJSON.optString(PAYLOAD_OS_NOTIFICATION_ID, null);
            else
                OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Not a OneSignal formatted JSON string. No 'i' field in custom.");
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Not a OneSignal formatted JSON String, error parsing string as JSON.");
        }
        return null;
    }
}
