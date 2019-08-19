package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

/**
 * Setter and Getter of Notifications received
 */
public class NotificationData {

    public static final String NOTIFICATION_ID = "notification_id";
    public static final String TIME = "time";
    public static final String APP_ACTIVE = "app_active";

    /**
     * Save state of last notification received
     */
    static void markLastNotificationReceived(@Nullable String notificationId) {
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Notification markLastNotificationReceived with id: " + notificationId);
        if (notificationId == null || notificationId.isEmpty())
            return;

        try {
            JSONObject jsonObject = new JSONObject()
                    .put(NOTIFICATION_ID, notificationId)
                    .put(APP_ACTIVE, OneSignal.isAppActive())
                    .put(TIME, new Date().getTime());
            OneSignalPrefs.saveString(OneSignalPrefs.PREFS_ONESIGNAL, OneSignalPrefs.PREFS_OS_LAST_NOTIFICATION_RECEIVED, jsonObject.toString());
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating direct notification arrived:JSON Failed.", e);
        }
    }

    @Nullable
    static String getLastNotificationReceivedData() {
        return OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL, OneSignalPrefs.PREFS_OS_LAST_NOTIFICATION_RECEIVED, null);
    }

    @Nullable
    static String getLastNotificationReceivedId() {
        String jsonString = OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL, OneSignalPrefs.PREFS_OS_LAST_NOTIFICATION_RECEIVED, null);
        if (jsonString != null) {
            try {
                JSONObject jsonObject = new JSONObject(jsonString);
                return jsonObject.getString(NotificationData.NOTIFICATION_ID);
            } catch (JSONException e) {
                OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Creating from string notification arrived id:JSON Failed.", e);
            }
        }
        return null;
    }
}
