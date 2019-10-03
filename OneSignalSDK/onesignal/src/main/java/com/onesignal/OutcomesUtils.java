package com.onesignal;

import android.os.Build;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

/**
 * Setter and Getter of Notifications received
 */
class OutcomesUtils {

    static final String NOTIFICATIONS_IDS = "notification_ids";
    static final String NOTIFICATION_ID = "notification_id";
    static final String TIME = "time";

    /**
     * Save state of last notification received
     */
    static void markLastNotificationReceived(@Nullable String notificationId) {
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Notification markLastNotificationReceived with id: " + notificationId);
        if (notificationId == null || notificationId.isEmpty())
            return;

        try {
            String object = OneSignalPrefs.getString(
                    OneSignalPrefs.PREFS_ONESIGNAL,
                    OneSignalPrefs.PREFS_OS_LAST_NOTIFICATIONS_RECEIVED,
                    "[]");

            JSONArray notificationsReceived = new JSONArray(object);
            JSONObject notification = new JSONObject()
                    .put(NOTIFICATION_ID, notificationId)
                    .put(TIME, new Date().getTime());
            notificationsReceived.put(notification);

            int notificationLimit = getNotificationLimit();
            JSONArray notificationsToSave = notificationsReceived;

            //Only save the last notifications ids without surpassing the limit
            //Always keep the max quantity of ids possible
            //If the attribution window increases, old notifications ids might influence the session
            if (notificationsReceived.length() > notificationLimit) {
                int lengthDifference = notificationsReceived.length() - notificationLimit;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    for (int i = 0; i < lengthDifference; i++) {
                        notificationsReceived.remove(i);
                    }
                } else {
                    notificationsToSave = new JSONArray();
                    for (int i = lengthDifference; i < notificationsReceived.length(); i++) {
                        notificationsToSave.put(notificationsReceived.get(i));
                    }
                }
            }

            OneSignalPrefs.saveString(OneSignalPrefs.PREFS_ONESIGNAL, OneSignalPrefs.PREFS_OS_LAST_NOTIFICATIONS_RECEIVED, notificationsToSave.toString());
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating direct notification arrived:JSON Failed.", e);
        }
    }

    static JSONArray getLastNotificationsReceivedData() {
        String notificationsReceived = OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL, OneSignalPrefs.PREFS_OS_LAST_NOTIFICATIONS_RECEIVED, "[]");
        try {
            return new JSONArray(notificationsReceived);
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating last notifications received data:JSON Failed.", e);
        }

        return new JSONArray();
    }

    static int getNotificationLimit() {
        return OneSignalPrefs.getInt(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_NOTIFICATION_LIMIT,
                OneSignalRemoteParams.DEFAULT_NOTIFICATION_LIMIT
        );
    }

    static int getIndirectAttributionWindow() {
        return OneSignalPrefs.getInt(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_INDIRECT_ATTRIBUTION_WINDOW,
                OneSignalRemoteParams.DEFAULT_INDIRECT_ATTRIBUTION_WINDOW
        );
    }
    
    static boolean isDirectSessionEnabled() {
        return OneSignalPrefs.getBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_DIRECT_ENABLED,
                false
        );
    }

    static boolean isIndirectSessionEnabled() {
        return OneSignalPrefs.getBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_INDIRECT_ENABLED,
                false
        );
    }

    static boolean isUnattributedSessionEnabled() {
        return OneSignalPrefs.getBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_UNATTRIBUTED_ENABLED,
                false
        );
    }

    static void saveOutcomesParams(OneSignalRemoteParams.OutcomesParams outcomesParams) {
        OneSignalPrefs.saveBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_DIRECT_ENABLED,
                outcomesParams.directEnabled
        );
        OneSignalPrefs.saveBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_INDIRECT_ENABLED,
                outcomesParams.indirectEnabled
        );
        OneSignalPrefs.saveBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_UNATTRIBUTED_ENABLED,
                outcomesParams.unattributedEnabled
        );
        OneSignalPrefs.saveInt(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_NOTIFICATION_LIMIT,
                outcomesParams.notificationLimit
        );
        OneSignalPrefs.saveInt(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_INDIRECT_ATTRIBUTION_WINDOW,
                outcomesParams.indirectAttributionWindow
        );
    }
}
