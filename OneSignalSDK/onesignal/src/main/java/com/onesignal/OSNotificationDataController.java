package com.onesignal;

import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.onesignal.OneSignalDbContract.NotificationTable;

import org.json.JSONObject;

import java.lang.ref.WeakReference;

class OSNotificationDataController extends OSBackgroundManager {

    private final static long NOTIFICATION_CACHE_DATA_LIFETIME = 604_800L; // 7 days in second

    private final static String OS_NOTIFICATIONS_THREAD = "OS_NOTIFICATIONS_THREAD";

    private final OneSignalDbHelper dbHelper;
    private final OSLogger logger;

    public OSNotificationDataController(OneSignalDbHelper dbHelper, OSLogger logger) {
        this.dbHelper = dbHelper;
        this.logger = logger;
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
     * Deletes notifications with created timestamps older than 7 days
     * Cleans notification tables
     * 1. NotificationTable.TABLE_NAME
     */
    private void cleanNotificationCache() {
        Runnable notificationCacheCleaner = new BackgroundRunnable() {
            @Override
            public void run() {
                super.run();

                String whereStr = NotificationTable.COLUMN_NAME_CREATED_TIME + " < ?";

                String sevenDaysAgoInSeconds = String.valueOf((OneSignal.getTime().getCurrentTimeMillis() / 1_000L) - NOTIFICATION_CACHE_DATA_LIFETIME);
                String[] whereArgs = new String[]{sevenDaysAgoInSeconds};

                dbHelper.delete(
                        NotificationTable.TABLE_NAME,
                        whereStr,
                        whereArgs);
            }
        };

        runRunnableOnThread(notificationCacheCleaner, OS_NOTIFICATIONS_THREAD);
    }

    void clearOneSignalNotifications(final WeakReference<Context> weakReference) {
        Runnable runClearOneSignalNotifications = new BackgroundRunnable() {
            @Override
            public void run() {
                super.run();

                Context appContext = weakReference.get();
                if (appContext == null)
                    return;

                NotificationManager notificationManager = OneSignalNotificationManager.getNotificationManager(appContext);

                String[] retColumn = {OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID};

                Cursor cursor = dbHelper.query(
                        OneSignalDbContract.NotificationTable.TABLE_NAME,
                        retColumn,
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                                OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0",
                        null,
                        null,                                                    // group by
                        null,                                                    // filter by row groups
                        null                                                     // sort order
                );

                if (cursor.moveToFirst()) {
                    do {
                        int existingId = cursor.getInt(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID));
                        notificationManager.cancel(existingId);
                    } while (cursor.moveToNext());
                }

                // Mark all notifications as dismissed unless they were already opened.
                String whereStr = NotificationTable.COLUMN_NAME_OPENED + " = 0";
                ContentValues values = new ContentValues();
                values.put(NotificationTable.COLUMN_NAME_DISMISSED, 1);
                dbHelper.update(NotificationTable.TABLE_NAME, values, whereStr, null);

                BadgeCountUpdater.updateCount(0, appContext);

                cursor.close();
            }
        };

        runRunnableOnThread(runClearOneSignalNotifications, OS_NOTIFICATIONS_THREAD);
    }

    void removeGroupedNotifications(final String group, final WeakReference<Context> weakReference) {
        Runnable runCancelGroupedNotifications = new BackgroundRunnable() {
            @Override
            public void run() {
                super.run();

                Context appContext = weakReference.get();
                if (appContext == null)
                    return;

                NotificationManager notificationManager = OneSignalNotificationManager.getNotificationManager(appContext);

                String[] retColumn = {NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID};

                final String[] whereArgs = {group};

                String whereStr = NotificationTable.COLUMN_NAME_GROUP_ID + " = ? AND " +
                        NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                        NotificationTable.COLUMN_NAME_OPENED + " = 0";

                Cursor cursor = dbHelper.query(
                        NotificationTable.TABLE_NAME,
                        retColumn,
                        whereStr,
                        whereArgs,
                        null, null, null);

                while (cursor.moveToNext()) {
                    int notificationId = cursor.getInt(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID));
                    if (notificationId != -1)
                        notificationManager.cancel(notificationId);
                }
                cursor.close();

                whereStr = NotificationTable.COLUMN_NAME_GROUP_ID + " = ? AND " +
                        NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
                        NotificationTable.COLUMN_NAME_DISMISSED + " = 0";

                ContentValues values = new ContentValues();
                values.put(NotificationTable.COLUMN_NAME_DISMISSED, 1);

                dbHelper.update(NotificationTable.TABLE_NAME, values, whereStr, whereArgs);
                BadgeCountUpdater.update(dbHelper, appContext);
            }
        };

        runRunnableOnThread(runCancelGroupedNotifications, OS_NOTIFICATIONS_THREAD);
    }

    void removeNotification(final int id, final WeakReference<Context> weakReference) {
        Runnable runCancelNotification = new BackgroundRunnable() {
            @Override
            public void run() {
                super.run();

                Context appContext = weakReference.get();
                if (appContext == null)
                    return;

                String whereStr = NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + id + " AND " +
                        NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
                        NotificationTable.COLUMN_NAME_DISMISSED + " = 0";

                ContentValues values = new ContentValues();
                values.put(NotificationTable.COLUMN_NAME_DISMISSED, 1);

                int records = dbHelper.update(NotificationTable.TABLE_NAME, values, whereStr, null);

                if (records > 0)
                    NotificationSummaryManager.updatePossibleDependentSummaryOnDismiss(appContext, dbHelper, id);
                BadgeCountUpdater.update(dbHelper, appContext);

                NotificationManager notificationManager = OneSignalNotificationManager.getNotificationManager(appContext);
                notificationManager.cancel(id);
            }
        };

        runRunnableOnThread(runCancelNotification, OS_NOTIFICATIONS_THREAD);
    }

    void notValidOrDuplicated(@Nullable JSONObject jsonPayload, @NonNull final InvalidOrDuplicateNotificationCallback callback) {
        String id = OSNotificationFormatHelper.getOSNotificationIdFromJson(jsonPayload);
        if (id == null) {
            logger.debug("Notification notValidOrDuplicated with id null");
            callback.onResult(true);
            return;
        }

        isDuplicateNotification(id, callback);
    }

    private void isDuplicateNotification(final String id, @NonNull final InvalidOrDuplicateNotificationCallback callback) {
        if (id == null || "".equals(id)) {
            callback.onResult(false);
            return;
        }

        if (!OSNotificationWorkManager.addNotificationIdProcessed(id)) {
            logger.debug("Notification notValidOrDuplicated with id duplicated");
            callback.onResult(true);
            return;
        }

        Runnable runCancelNotification = new BackgroundRunnable() {
            @Override
            public void run() {
                super.run();

                boolean result = false;
                String[] retColumn = {NotificationTable.COLUMN_NAME_NOTIFICATION_ID};
                String[] whereArgs = {id};

                Cursor cursor = dbHelper.query(
                        NotificationTable.TABLE_NAME,
                        retColumn,
                        NotificationTable.COLUMN_NAME_NOTIFICATION_ID + " = ?",   // Where String
                        whereArgs,
                        null, null, null);

                boolean exists = cursor.moveToFirst();

                cursor.close();

                if (exists) {
                    logger.debug("Notification notValidOrDuplicated with id duplicated, duplicate FCM message received, skip processing of " + id);
                    result = true;
                }

                callback.onResult(result);
            }
        };

        runRunnableOnThread(runCancelNotification, OS_NOTIFICATIONS_THREAD);
    }

    interface InvalidOrDuplicateNotificationCallback {

        /**
         * @param result is true if notification is invalid or duplicated, otherwise false
         */
        void onResult(boolean result);
    }
}
