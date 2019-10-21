package com.onesignal;

import com.onesignal.OneSignalDbContract.NotificationTable;
import com.onesignal.OneSignalDbContract.CachedUniqueOutcomeNotificationTable;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Process;

class OneSignalCacheCleaner {

    private static String OS_DELETE_OLD_CACHED_DATA = "OS_DELETE_OLD_CACHED_DATA";

    /**
     * We clean outdated cache from several places within the OneSignal SDK here
     * 1. In App Messaging id sets (impressions, clicks, views)
     * 2. Notifications after 1 week
     * 3. Unique outcome events linked to notification ids (1 week)
     */
    synchronized static void cleanOldCachedData(final Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);
                OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(context);
                SQLiteDatabase writableDb = dbHelper.getWritableDbWithRetries();

                cleanInAppMessagingCache();
                cleanNotificationCache(writableDb);
            }
        }, OS_DELETE_OLD_CACHED_DATA).start();
    }

    /**
     * TODO: Needs to be implemented to clean out old IAM data used to track impressions, clicks, and viewed IAMs
     */
    static void cleanInAppMessagingCache() {
        // NOTE: Currently IAMs will pile up overtime and since IAMs can be modified, active, inactive, etc.
        //  we never truly know when it is the correct time to remove these ids form our cache
    }

    /**
     * Cleans two notification tables
     * 1. NotificationTable.TABLE_NAME
     * 2. CachedUniqueOutcomeNotificationTable.TABLE_NAME
     */
    static void cleanNotificationCache(SQLiteDatabase writableDb) {
        cleanOldNotificationData(writableDb);
        cleanOldUniqueOutcomeEventNotificationsCache(writableDb);
    }

    /**
     * Deletes any notifications with created timestamps older than 7 days
     */
    private static void cleanOldNotificationData(SQLiteDatabase writableDb) {
        writableDb.delete(NotificationTable.TABLE_NAME,
                NotificationTable.COLUMN_NAME_CREATED_TIME + " < " + ((System.currentTimeMillis() / 1_000L) - 604_800L),
                null);
    }

    /**
     * Deletes any notifications whose ids do not exist inside of the NotificationTable.TABLE_NAME
     */
    static void cleanOldUniqueOutcomeEventNotificationsCache(SQLiteDatabase writableDb) {
        writableDb.delete(CachedUniqueOutcomeNotificationTable.TABLE_NAME,
                "NOT EXISTS(SELECT NULL FROM " + NotificationTable.TABLE_NAME +
                        " n WHERE" +
                        " n." + NotificationTable.COLUMN_NAME_NOTIFICATION_ID  + " = " + CachedUniqueOutcomeNotificationTable.COLUMN_NAME_NOTIFICATION_ID + ")",
                null);
    }

}
