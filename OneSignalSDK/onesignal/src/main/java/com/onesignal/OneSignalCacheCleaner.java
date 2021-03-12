package com.onesignal;

import android.content.Context;
import android.os.Process;

import androidx.annotation.WorkerThread;

import com.onesignal.OneSignalDbContract.NotificationTable;
import com.onesignal.influence.domain.OSInfluenceChannel;
import com.onesignal.outcomes.data.OutcomesDbContract;

class OneSignalCacheCleaner {

    private final static long NOTIFICATION_CACHE_DATA_LIFETIME = 604_800L; // 7 days in second

    private final static String OS_DELETE_CACHED_NOTIFICATIONS_THREAD = "OS_DELETE_CACHED_NOTIFICATIONS_THREAD";
    private final static String OS_DELETE_CACHED_REDISPLAYED_IAMS_THREAD = "OS_DELETE_CACHED_REDISPLAYED_IAMS_THREAD";

    /**
     * We clean outdated cache from several places within the OneSignal SDK here
     * 1. Notifications & unique outcome events linked to notification ids (1 week)
     * 2. Cached In App Messaging Sets in SharedPreferences (impressions, clicks, views) and SQL IAMs
     */
    static void cleanOldCachedData(final Context context) {
        OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(context);
        cleanNotificationCache(dbHelper);
        cleanCachedInAppMessages(dbHelper);
    }

    /**
     * Cleans two notification tables
     * 1. NotificationTable.TABLE_NAME
     * 2. CachedUniqueOutcomeNotificationTable.TABLE_NAME
     */
    synchronized static void cleanNotificationCache(final OneSignalDbHelper writableDb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);

                cleanCachedNotifications(writableDb);
            }

        }, OS_DELETE_CACHED_NOTIFICATIONS_THREAD).start();
    }

    /**
     * Remove IAMs that the last display time was six month ago
     * 1. Query for all old message ids and old clicked click ids
     * 2. Delete old IAMs from SQL
     * 3. Use queried data to clean SharedPreferences
     */
    @WorkerThread
    static synchronized void cleanCachedInAppMessages(final OneSignalDbHelper dbHelper) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);

                OSInAppMessageRepository inAppMessageRepository = OneSignal
                        .getInAppMessageController()
                        .getInAppMessageRepository(dbHelper);
                inAppMessageRepository.cleanCachedInAppMessages();
            }
        }, OS_DELETE_CACHED_REDISPLAYED_IAMS_THREAD).start();
    }

    /**
     * Deletes notifications with created timestamps older than 7 days
     * <br/><br/>
     * Note: This should only ever be called by {@link OneSignalCacheCleaner#cleanNotificationCache(OneSignalDbHelper)}
     * <br/><br/>
     *
     * @see OneSignalCacheCleaner#cleanNotificationCache(OneSignalDbHelper)
     */
    private static void cleanCachedNotifications(OneSignalDbHelper writableDb) {
        String whereStr = NotificationTable.COLUMN_NAME_CREATED_TIME + " < ?";

        String sevenDaysAgoInSeconds = String.valueOf((OneSignal.getTime().getCurrentTimeMillis() / 1_000L) - NOTIFICATION_CACHE_DATA_LIFETIME);
        String[] whereArgs = new String[]{ sevenDaysAgoInSeconds };

        writableDb.delete(
                NotificationTable.TABLE_NAME,
                whereStr,
                whereArgs);
    }

}
