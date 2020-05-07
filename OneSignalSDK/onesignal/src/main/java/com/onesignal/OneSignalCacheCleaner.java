package com.onesignal;

import com.onesignal.OneSignalDbContract.NotificationTable;
import com.onesignal.OneSignalDbContract.CachedUniqueOutcomeNotificationTable;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Process;
import android.support.annotation.WorkerThread;

import java.util.Set;

class OneSignalCacheCleaner {

    private final static long ONE_WEEK_IN_MILLIS = 604_800L;
    private final static long SIX_MONTHS_IN_MILLIS = 15_552_000L;

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

            /**
             * Deletes notifications with created timestamps older than 7 days
             */
            private void cleanCachedNotifications(SQLiteDatabase writableDb) {
                String whereStr = NotificationTable.COLUMN_NAME_CREATED_TIME + " < ?";

                String sevenDaysAgoInSeconds = String.valueOf((System.currentTimeMillis() / 1_000L) - ONE_WEEK_IN_MILLIS);
                String[] whereArgs = new String[]{ sevenDaysAgoInSeconds };

                writableDb.delete(
                        NotificationTable.TABLE_NAME,
                        whereStr,
                        whereArgs);
            }

            /**
             * Deletes cached unique outcome notifications whose ids do not exist inside of the NotificationTable.TABLE_NAME
             */
            private void cleanCachedUniqueOutcomeEventNotifications(SQLiteDatabase writableDb) {
                String whereStr = "NOT EXISTS(SELECT NULL FROM " + NotificationTable.TABLE_NAME +
                        " n WHERE" +
                        " n." + NotificationTable.COLUMN_NAME_NOTIFICATION_ID +
                        " = " + CachedUniqueOutcomeNotificationTable.COLUMN_NAME_NOTIFICATION_ID + ")";

                writableDb.delete(
                        CachedUniqueOutcomeNotificationTable.TABLE_NAME,
                        whereStr,
                        null);
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

                String sixMonthsAgoInSeconds = String.valueOf((System.currentTimeMillis() / 1_000L) - SIX_MONTHS_IN_MILLIS);
                String[] whereArgs = new String[]{sixMonthsAgoInSeconds};

                Cursor cursor = writableDb.query(OneSignalDbContract.InAppMessageTable.TABLE_NAME,
                        retColumns,
                        whereStr,
                        whereArgs,
                        null,
                        null,
                        null);

                // From cursor get all of the old message ids and old clicked click ids
                Set<String> oldMessageIds = OSUtils.newConcurrentSet();
                Set<String> oldClickedClickIds = OSUtils.newConcurrentSet();
                if (cursor.moveToFirst()) {
                    do {
                        String oldMessageId = cursor.getString(
                                cursor.getColumnIndex(
                                        OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID));
                        String oldClickIds = cursor.getString(
                                cursor.getColumnIndex(
                                        OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID));

                        oldMessageIds.add(oldMessageId);
                        oldClickedClickIds.addAll(OSUtils.newStringSetFromString(oldClickIds));
                    } while (cursor.moveToNext());
                }
                cursor.close();

                // 2. Delete old IAMs from SQL
                writableDb.delete(
                        OneSignalDbContract.InAppMessageTable.TABLE_NAME,
                        whereStr,
                        whereArgs);

                // 3. Use queried data to clean SharedPreferences
                cleanCachedSharedPreferenceIamData(oldMessageIds, oldClickedClickIds);
            }

            private void cleanCachedSharedPreferenceIamData(Set<String> oldMessageIds, Set<String> oldClickedClickIds) {
                // IAMs without redisplay on with pile up and we need to clean these for dismissing, impressions, and clicks
                Set<String> dismissedMessages = OneSignalPrefs.getStringSet(
                        OneSignalPrefs.PREFS_ONESIGNAL,
                        OneSignalPrefs.PREFS_OS_DISMISSED_IAMS,
                        OSUtils.<String>newConcurrentSet());

                Set<String> impressionedMessages = OneSignalPrefs.getStringSet(
                        OneSignalPrefs.PREFS_ONESIGNAL,
                        OneSignalPrefs.PREFS_OS_IMPRESSIONED_IAMS,
                        OSUtils.<String>newConcurrentSet());

                Set<String> clickedClickIds = OneSignalPrefs.getStringSet(
                        OneSignalPrefs.PREFS_ONESIGNAL,
                        OneSignalPrefs.PREFS_OS_CLICKED_CLICK_IDS_IAMS,
                        OSUtils.<String>newConcurrentSet());

                if (oldMessageIds != null) {
                    if (dismissedMessages != null) {
                        dismissedMessages.removeAll(oldMessageIds);
                        OneSignalPrefs.saveStringSet(
                                OneSignalPrefs.PREFS_ONESIGNAL,
                                OneSignalPrefs.PREFS_OS_DISMISSED_IAMS,
                                dismissedMessages);
                    }

                    if (impressionedMessages != null) {
                        impressionedMessages.removeAll(oldMessageIds);
                        OneSignalPrefs.saveStringSet(
                                OneSignalPrefs.PREFS_ONESIGNAL,
                                OneSignalPrefs.PREFS_OS_IMPRESSIONED_IAMS,
                                impressionedMessages);
                    }
                }

                if (clickedClickIds != null && oldClickedClickIds != null) {
                    clickedClickIds.removeAll(oldClickedClickIds);
                    OneSignalPrefs.saveStringSet(
                            OneSignalPrefs.PREFS_ONESIGNAL,
                            OneSignalPrefs.PREFS_OS_CLICKED_CLICK_IDS_IAMS,
                            clickedClickIds);
                }

            }

        }, OS_DELETE_CACHED_REDISPLAYED_IAMS_THREAD).start();
    }

}
