package com.onesignal;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Process;

import androidx.annotation.WorkerThread;

import com.onesignal.OneSignalDbContract.NotificationTable;
import com.onesignal.influence.domain.OSInfluenceChannel;
import com.onesignal.outcomes.data.OSOutcomeTableProvider;
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
        SQLiteDatabase writableDb = dbHelper.getSQLiteDatabaseWithRetries();

        cleanNotificationCache(writableDb);
        cleanCachedInAppMessages(dbHelper);
    }

    /**
     * Cleans two notification tables
     * 1. NotificationTable.TABLE_NAME
     * 2. CachedUniqueOutcomeNotificationTable.TABLE_NAME
     */
    synchronized static void cleanNotificationCache(final SQLiteDatabase writableDb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);

                writableDb.beginTransaction();
                try {
                    cleanCachedNotifications(writableDb);
                    cleanCachedUniqueOutcomeEventNotifications(writableDb);
                    writableDb.setTransactionSuccessful();
                } finally {
                    try {
                        writableDb.endTransaction(); // May throw if transaction was never opened or DB is full.
                    } catch (SQLException t) {
                        OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error closing transaction! ", t);
                    }
                }
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
    synchronized static void cleanCachedInAppMessages(final OneSignalDbHelper dbHelper) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);

                OSInAppMessageRepository inAppMessageRepository = OSInAppMessageController
                        .getController()
                        .getInAppMessageRepository(dbHelper);
                inAppMessageRepository.cleanCachedInAppMessages();
            }
        }, OS_DELETE_CACHED_REDISPLAYED_IAMS_THREAD).start();
    }

    /**
     * Deletes notifications with created timestamps older than 7 days
     * <br/><br/>
     * Note: This should only ever be called by {@link OneSignalCacheCleaner#cleanNotificationCache(SQLiteDatabase)}
     * <br/><br/>
     * @see OneSignalCacheCleaner#cleanNotificationCache(SQLiteDatabase)
     */
    private static void cleanCachedNotifications(SQLiteDatabase writableDb) {
        String whereStr = NotificationTable.COLUMN_NAME_CREATED_TIME + " < ?";

        String sevenDaysAgoInSeconds = String.valueOf((OneSignal.getTime().getCurrentTimeMillis() / 1_000L) - NOTIFICATION_CACHE_DATA_LIFETIME);
        String[] whereArgs = new String[]{ sevenDaysAgoInSeconds };

        writableDb.delete(
                NotificationTable.TABLE_NAME,
                whereStr,
                whereArgs);
    }

    /**
     * Deletes cached unique outcome notifications whose ids do not exist inside of the NotificationTable.TABLE_NAME
     * <br/><br/>
     * Note: This should only ever be called by {@link OneSignalCacheCleaner#cleanNotificationCache(SQLiteDatabase)}
     * <br/><br/>
     * @see OneSignalCacheCleaner#cleanNotificationCache(SQLiteDatabase)
     */
    private static void cleanCachedUniqueOutcomeEventNotifications(SQLiteDatabase writableDb) {
        String whereStr = "NOT EXISTS(" +
                "SELECT NULL FROM " + NotificationTable.TABLE_NAME + " n " +
                "WHERE" + " n." + NotificationTable.COLUMN_NAME_NOTIFICATION_ID + " = " + OutcomesDbContract.CACHE_UNIQUE_OUTCOME_COLUMN_CHANNEL_INFLUENCE_ID +
                " AND " + OutcomesDbContract.CACHE_UNIQUE_OUTCOME_COLUMN_CHANNEL_TYPE + " = \"" + OSInfluenceChannel.NOTIFICATION.toString().toLowerCase() +
                "\")";

        writableDb.delete(
                OutcomesDbContract.CACHE_UNIQUE_OUTCOME_TABLE,
                whereStr,
                null);
    }

}
