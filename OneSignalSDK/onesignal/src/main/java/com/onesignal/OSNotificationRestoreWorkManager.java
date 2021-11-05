package com.onesignal;

import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

class OSNotificationRestoreWorkManager {

    static final String[] COLUMNS_FOR_RESTORE = {
            OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID,
            OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID,
            OneSignalDbContract.NotificationTable.COLUMN_NAME_FULL_DATA,
            OneSignalDbContract.NotificationTable.COLUMN_NAME_CREATED_TIME
    };

    // Delay to prevent logcat messages and possibly skipping some notifications
    //    This prevents the following error;
    // E/NotificationService: Package enqueue rate is 10.56985. Shedding events. package=####
    private static final int DELAY_BETWEEN_NOTIFICATION_RESTORES_MS = 200;
    private static final String NOTIFICATION_RESTORE_WORKER_IDENTIFIER = NotificationRestoreWorker.class.getCanonicalName();

    static final int DEFAULT_TTL_IF_NOT_IN_PAYLOAD = 259_200;

    // Notifications will never be force removed when the app's process is running,
    //   so we only need to restore at most once per cold start of the app.
    public static boolean restored;

    public static void beginEnqueueingWork(Context context, boolean shouldDelay) {
        // When boot or upgrade, add a 15 second delay to alleviate app doing to much work all at once
        int restoreDelayInSeconds = shouldDelay ? 15 : 0;

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(NotificationRestoreWorker.class)
                .setInitialDelay(restoreDelayInSeconds, TimeUnit.SECONDS)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniqueWork(NOTIFICATION_RESTORE_WORKER_IDENTIFIER, ExistingWorkPolicy.KEEP, workRequest);
    }

    public static class NotificationRestoreWorker extends Worker {

        public NotificationRestoreWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            Context context = getApplicationContext();

            if (OneSignal.appContext == null)
                OneSignal.initWithContext(context);

            if (!OSUtils.areNotificationsEnabled(context))
                return Result.failure();

            if (restored)
                return Result.failure();
            restored = true;

            OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "Restoring notifications");

            OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(context);

            StringBuilder dbQuerySelection = OneSignalDbHelper.recentUninteractedWithNotificationsWhere();
            skipVisibleNotifications(context, dbQuerySelection);

            queryAndRestoreNotificationsAndBadgeCount(context, dbHelper, dbQuerySelection);

            return Result.success();
        }

    }

    private static void queryAndRestoreNotificationsAndBadgeCount(
            Context context,
            OneSignalDbHelper dbHelper,
            StringBuilder dbQuerySelection) {

        OneSignal.Log(OneSignal.LOG_LEVEL.INFO,
                "Querying DB for notifications to restore: " + dbQuerySelection.toString());

        Cursor cursor = null;
        try {
            cursor = dbHelper.query(
                    OneSignalDbContract.NotificationTable.TABLE_NAME,
                    COLUMNS_FOR_RESTORE,
                    dbQuerySelection.toString(),
                    null,
                    null, // group by
                    null, // filter by row groups
                    OneSignalDbContract.NotificationTable._ID + " DESC", // sort order, new to old
                    NotificationLimitManager.MAX_NUMBER_OF_NOTIFICATIONS_STR // limit
            );
            showNotificationsFromCursor(context, cursor, DELAY_BETWEEN_NOTIFICATION_RESTORES_MS);
            BadgeCountUpdater.update(dbHelper, context);
        } catch (Throwable t) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error restoring notification records! ", t);
        } finally {
            if (cursor != null && !cursor.isClosed())
                cursor.close();
        }
    }

    /**
     * Retrieve the list of notifications that are currently in the shade
     *    this is used to prevent notifications from being restored twice in M and newer.
     * This is important mostly for Android O as they can't be redisplayed in a silent way unless
     *    they are displayed under a different channel which isn't ideal.
     * For pre-O devices this still have the benefit of being more efficient
     */
    private static void skipVisibleNotifications(Context context, StringBuilder dbQuerySelection) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return;

        StatusBarNotification[] activeNotifs = OneSignalNotificationManager.getActiveNotifications(context);
        if (activeNotifs.length == 0)
            return;

        ArrayList<Integer> activeNotifIds = new ArrayList<>();
        for (StatusBarNotification activeNotif : activeNotifs)
            activeNotifIds.add(activeNotif.getId());

        dbQuerySelection
                .append(" AND " + OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " NOT IN (")
                .append(TextUtils.join(",", activeNotifIds))
                .append(")");
    }

    /**
     * Restores a set of notifications back to the notification shade based on an SQL cursor
     * @param cursor - Source cursor to generate notifications from
     * @param delay - Delay to slow down process to ensure we don't spike CPU and I/O on the device
     */
    static void showNotificationsFromCursor(Context context, Cursor cursor, int delay) {
        if (!cursor.moveToFirst())
            return;

        do {
            String osNotificationId = cursor.getString(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID));
            int existingId = cursor.getInt(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID));
            String fullData = cursor.getString(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_FULL_DATA));
            long dateTime = cursor.getLong(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_CREATED_TIME));

            OSNotificationWorkManager.beginEnqueueingWork(
                    context,
                    osNotificationId,
                    existingId,
                    fullData,
                    dateTime,
                    true,
                    false
            );

            if (delay > 0)
                OSUtils.sleep(delay);
        } while (cursor.moveToNext());
    }
}
