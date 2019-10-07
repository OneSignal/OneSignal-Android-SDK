package com.onesignal;

import android.support.annotation.Nullable;

import org.json.JSONArray;

public class MockOutcomesUtils {

    public void markLastNotificationReceived(@Nullable String notificationId) {
        OutcomesUtils.markLastNotificationReceived(notificationId);
    }

    public JSONArray getLastNotificationsReceivedData() {
        return OutcomesUtils.getLastNotificationsReceivedData();
    }

    public int getNotificationLimit() {
        return OutcomesUtils.getNotificationLimit();
    }

    public void clearNotificationSharedPreferences() {
        OneSignalPrefs.saveString(OneSignalPrefs.PREFS_ONESIGNAL, OneSignalPrefs.PREFS_OS_LAST_NOTIFICATIONS_RECEIVED, "[]");
    }

    public void saveOutcomesParams(OneSignalRemoteParams.OutcomesParams outcomesParams) {
        OutcomesUtils.saveOutcomesParams(outcomesParams);
    }
}