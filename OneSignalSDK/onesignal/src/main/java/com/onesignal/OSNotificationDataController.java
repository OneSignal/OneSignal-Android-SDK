package com.onesignal;

import android.os.Process;

import com.onesignal.OneSignalDbContract.NotificationTable;

class OSNotificationDataController {

    private final static long NOTIFICATION_CACHE_DATA_LIFETIME = 604_800L; // 7 days in second

    private final static String OS_DELETE_CACHED_NOTIFICATIONS_THREAD = "OS_DELETE_CACHED_NOTIFICATIONS_THREAD";

    private final OneSignalDbHelper dbHelper;

    public OSNotificationDataController(OneSignalDbHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    /**
     * We clean outdated cache from several places within the OneSignal SDK here
     * 1. Notifications & unique outcome events linked to notification ids (1 week)
     * 2. Cached In App Messaging Sets in SharedPreferences (impressions, clicks, views) and SQL IAMs
     */
    void cleanOldCachedData() {
        cleanNotificationCache();
    }

    /**
     * Cleans two notification tables
     * 1. NotificationTable.TABLE_NAME
     * 2. CachedUniqueOutcomeNotificationTable.TABLE_NAME
     */
    private void cleanNotificationCache() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);

                cleanCachedNotifications(dbHelper);
            }

        }, OS_DELETE_CACHED_NOTIFICATIONS_THREAD).start();
    }

    /**
     * Deletes notifications with created timestamps older than 7 days
     */
    private void cleanCachedNotifications(OneSignalDb writableDb) {
        String whereStr = NotificationTable.COLUMN_NAME_CREATED_TIME + " < ?";

        String sevenDaysAgoInSeconds = String.valueOf((OneSignal.getTime().getCurrentTimeMillis() / 1_000L) - NOTIFICATION_CACHE_DATA_LIFETIME);
        String[] whereArgs = new String[]{sevenDaysAgoInSeconds};

        writableDb.delete(
                NotificationTable.TABLE_NAME,
                whereStr,
                whereArgs);
    }

}
