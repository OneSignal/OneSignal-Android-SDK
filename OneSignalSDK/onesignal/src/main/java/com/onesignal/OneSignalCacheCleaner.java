package com.onesignal;

import com.onesignal.OneSignalDbContract.NotificationTable;
import com.onesignal.OneSignalDbContract.CachedUniqueOutcomeNotificationTable;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Process;
import android.support.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Set;

class OneSignalCacheCleaner {

    private final static long NOTIFICATION_CACHE_DATA_LIFETIME = 604_800L; // 7 days in seconds
    private final static long IAM_CACHE_DATA_LIFETIME = 15_552_000L; // 6 months in seconds

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
        cleanCachedInAppMessages(writableDb);
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

                cleanCachedNotifications(writableDb);
                cleanCachedUniqueOutcomeEventNotifications(writableDb);
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
    synchronized static void cleanCachedInAppMessages(final SQLiteDatabase writableDb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);

                // 1. Query for all old message ids and old clicked click ids
                String[] retColumns = new String[]{
                        OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID,
                        OneSignalDbContract.InAppMessageTable.COLUMN_CLICK_IDS
                };

                String whereStr = OneSignalDbContract.InAppMessageTable.COLUMN_NAME_LAST_DISPLAY + " < ?";

                String sixMonthsAgoInSeconds = String.valueOf((System.currentTimeMillis() / 1_000L) - IAM_CACHE_DATA_LIFETIME);
                String[] whereArgs = new String[]{sixMonthsAgoInSeconds};

                Set<String> oldMessageIds = OSUtils.newConcurrentSet();
                Set<String> oldClickedClickIds = OSUtils.newConcurrentSet();

                Cursor cursor = null;
                try {
                    cursor = writableDb.query(OneSignalDbContract.InAppMessageTable.TABLE_NAME,
                            retColumns,
                            whereStr,
                            whereArgs,
                            null,
                            null,
                            null);

                    if (cursor == null || cursor.getCount() == 0) {
                        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "Attempted to clean 6 month old IAM data, but none exists!");
                        return;
                    }

                    // From cursor get all of the old message ids and old clicked click ids
                    if (cursor.moveToFirst()) {
                        do {
                            String oldMessageId = cursor.getString(
                                    cursor.getColumnIndex(
                                            OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID));
                            String oldClickIds = cursor.getString(
                                    cursor.getColumnIndex(
                                            OneSignalDbContract.InAppMessageTable.COLUMN_CLICK_IDS));

                            oldMessageIds.add(oldMessageId);
                            oldClickedClickIds.addAll(OSUtils.newStringSetFromJSONArray(new JSONArray(oldClickIds)));
                        } while (cursor.moveToNext());
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                } finally {
                    if (cursor != null & !cursor.isClosed())
                        cursor.close();
                }

                // 2. Delete old IAMs from SQL
                writableDb.delete(
                        OneSignalDbContract.InAppMessageTable.TABLE_NAME,
                        whereStr,
                        whereArgs);

                // 3. Use queried data to clean SharedPreferences
                cleanInAppMessageIds(oldMessageIds);
                cleanInAppMessageClickedClickIds(oldClickedClickIds);
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

        String sevenDaysAgoInSeconds = String.valueOf((System.currentTimeMillis() / 1_000L) - NOTIFICATION_CACHE_DATA_LIFETIME);
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
                "WHERE" + " n." + NotificationTable.COLUMN_NAME_NOTIFICATION_ID + " = " + CachedUniqueOutcomeNotificationTable.COLUMN_NAME_NOTIFICATION_ID + ")";

        writableDb.delete(
                CachedUniqueOutcomeNotificationTable.TABLE_NAME,
                whereStr,
                null);
    }

    /**
     * Clean up 6 month old IAM ids in {@link android.content.SharedPreferences}:
     *  1. Dismissed message ids
     *  2. Impressioned message ids
     * <br/><br/>
     * Note: This should only ever be called by {@link OneSignalCacheCleaner#cleanCachedInAppMessages(SQLiteDatabase)}
     * <br/><br/>
     * @see OneSignalCacheCleaner#cleanCachedInAppMessages(SQLiteDatabase)
     */
    private static void cleanInAppMessageIds(Set<String> oldMessageIds) {
        if (oldMessageIds != null && oldMessageIds.size() > 0) {
            Set<String> dismissedMessages = OneSignalPrefs.getStringSet(
                    OneSignalPrefs.PREFS_ONESIGNAL,
                    OneSignalPrefs.PREFS_OS_DISMISSED_IAMS,
                    OSUtils.<String>newConcurrentSet());

            Set<String> impressionedMessages = OneSignalPrefs.getStringSet(
                    OneSignalPrefs.PREFS_ONESIGNAL,
                    OneSignalPrefs.PREFS_OS_IMPRESSIONED_IAMS,
                    OSUtils.<String>newConcurrentSet());

            if (dismissedMessages != null && dismissedMessages.size() > 0) {
                dismissedMessages.removeAll(oldMessageIds);
                OneSignalPrefs.saveStringSet(
                        OneSignalPrefs.PREFS_ONESIGNAL,
                        OneSignalPrefs.PREFS_OS_DISMISSED_IAMS,
                        dismissedMessages);
            }

            if (impressionedMessages != null && impressionedMessages.size() > 0) {
                impressionedMessages.removeAll(oldMessageIds);
                OneSignalPrefs.saveStringSet(
                        OneSignalPrefs.PREFS_ONESIGNAL,
                        OneSignalPrefs.PREFS_OS_IMPRESSIONED_IAMS,
                        impressionedMessages);
            }
        }
    }

    /**
     * Clean up 6 month old IAM clicked click ids in {@link android.content.SharedPreferences}:
     *  1. Clicked click ids from elements within IAM
     * <br/><br/>
     * Note: This should only ever be called by {@link OneSignalCacheCleaner#cleanCachedInAppMessages(SQLiteDatabase)}
     * <br/><br/>
     * @see OneSignalCacheCleaner#cleanCachedInAppMessages(SQLiteDatabase)
     */
    private static void cleanInAppMessageClickedClickIds(Set<String> oldClickedClickIds) {
        if (oldClickedClickIds != null && oldClickedClickIds.size() > 0) {
            Set<String> clickedClickIds = OneSignalPrefs.getStringSet(
                    OneSignalPrefs.PREFS_ONESIGNAL,
                    OneSignalPrefs.PREFS_OS_CLICKED_CLICK_IDS_IAMS,
                    OSUtils.<String>newConcurrentSet());

            if (clickedClickIds != null && clickedClickIds.size() > 0) {
                clickedClickIds.removeAll(oldClickedClickIds);
                OneSignalPrefs.saveStringSet(
                        OneSignalPrefs.PREFS_ONESIGNAL,
                        OneSignalPrefs.PREFS_OS_CLICKED_CLICK_IDS_IAMS,
                        clickedClickIds);
            }
        }
    }

}
