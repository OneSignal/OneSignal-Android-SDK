package com.onesignal;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import java.util.ArrayList;

public class OneSignalNotificationManager {

    private static final String GROUPLESS_SUMMARY_KEY = "os_group_undefined";
    private static final int GROUPLESS_SUMMARY_ID = -718463522;


    static String getGrouplessSummaryKey() {
        return GROUPLESS_SUMMARY_KEY;
    }

    static int getGrouplessSummaryId() {
        return GROUPLESS_SUMMARY_ID;
    }

    /**
     * Getter for obtaining NOTIFICATION_SERVICE system service
     */
    static NotificationManager getNotificationManager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Iterate over all active notifications and count the groupless ones
     * and return the int count
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    static Integer getGrouplessNotifsCount(Context context) {
        StatusBarNotification[] statusBarNotifications = getActiveNotifications(context);

        int groupCount = 0;
        for (StatusBarNotification statusBarNotification : statusBarNotifications) {
            if (!NotificationCompat.isGroupSummary(statusBarNotification.getNotification())
                    && GROUPLESS_SUMMARY_KEY.equals(statusBarNotification.getNotification().getGroup())) {
                groupCount++;
            }
        }
        return groupCount;
    }

    /**
     * Getter for obtaining all active notifications
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    static StatusBarNotification[] getActiveNotifications(Context context) {
        StatusBarNotification[] statusBarNotifications = new StatusBarNotification[]{};
        try {
            statusBarNotifications = getNotificationManager(context).getActiveNotifications();
        } catch(Throwable e) {
            // try-catch for Android 6.0.X and possibly 8.0.0 bug work around,
            //    getActiveNotifications sometimes throws a fatal exception.
            // Seem to be related to what Android's internal method getAppActiveNotifications returns.
            // Issue #422
        }
        return statusBarNotifications;
    }

    /**
     * Getter for obtaining any groupless notifications
     * A groupless notification is:
     * 1. Not a summary
     * 2. A null group key or group key using assigned GROUPLESS_SUMMARY_KEY
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    static ArrayList<StatusBarNotification> getActiveGrouplessNotifications(Context context) {
        ArrayList<StatusBarNotification> grouplessStatusBarNotifications = new ArrayList<>();

        /* Iterate over all active notifications and add the groupless non-summary
         * notifications to a ArrayList to be returned */
        StatusBarNotification[] statusBarNotifications = getActiveNotifications(context);
        for (StatusBarNotification statusBarNotification : statusBarNotifications) {
            Notification notification = statusBarNotification.getNotification();

            boolean isGroupSummary = NotificationLimitManager.isGroupSummary(statusBarNotification);
            boolean isGroupless = notification.getGroup() == null
                    || notification.getGroup().equals(OneSignalNotificationManager.getGrouplessSummaryKey());
            if (!isGroupSummary && isGroupless)
                grouplessStatusBarNotifications.add(statusBarNotification);
        }
        return grouplessStatusBarNotifications;
    }

    /**
     * All groupless notifications are assigned the GROUPLESS_SUMMARY_KEY and notify() is called
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    static void assignGrouplessNotifications(Context context, ArrayList<StatusBarNotification> grouplessNotifs) {
        for (StatusBarNotification grouplessNotif : grouplessNotifs) {
            Notification.Builder grouplessNotifBuilder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                grouplessNotifBuilder = Notification.Builder.recoverBuilder(context, grouplessNotif.getNotification());
            } else {
                grouplessNotifBuilder = new Notification.Builder(context);
            }

            // Recreate the notification but with the groupless key instead
            Notification notif = grouplessNotifBuilder
                    .setGroup(GROUPLESS_SUMMARY_KEY)
                    .build();

            NotificationManagerCompat.from(context).notify(grouplessNotif.getId(), notif);
        }
    }

    /**
     * Query SQLiteDatabase by group to find the most recent created notification id
     */
    static Integer getMostRecentNotifIdFromGroup(OneSignalDbHelper db, String group, boolean isGroupless) {
        /* Beginning of the query string changes based on being groupless or not
         * since the groupless notifications will have a null group key in the db */
        String whereStr = isGroupless ?
                OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID + " IS NULL" :
                OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID + " = ?";

        // Look for all active (not opened and not dismissed) notifications, not including summaries
        whereStr += " AND " +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_IS_SUMMARY + " = 0";
        String[] whereArgs = isGroupless ?
                null :
                new String[]{group};

        // Order by timestamp in descending and limit to 1
        Cursor cursor = db.query(OneSignalDbContract.NotificationTable.TABLE_NAME,
                null,
                whereStr,
                whereArgs,
                null,
                null,
                OneSignalDbContract.NotificationTable.COLUMN_NAME_CREATED_TIME + " DESC",
                "1");

        boolean hasRecord = cursor.moveToFirst();

        if (!hasRecord) {
            cursor.close();
            return null;
        }

        // Get more recent notification id from Cursor
        Integer recentId = cursor.getInt(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID));

        cursor.close();

        return recentId;
    }
}
