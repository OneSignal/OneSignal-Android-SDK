package com.onesignal;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationManagerCompat;

import java.util.ArrayList;

class OneSignalNotificationManager {

    static String GROUPLESS_SUMMARY_KEY = "os_group_undefined";
    static int GROUPLESS_SUMMARY_ID = -718463522;

    static NotificationManager getNotificationManager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    static StatusBarNotification[] getActiveNotifications(Context context) {
        return getNotificationManager(context).getActiveNotifications();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    static ArrayList<StatusBarNotification> getActiveGrouplessNotifications(Context context) {
        ArrayList<StatusBarNotification> grouplessStatusBarNotifications = new ArrayList<>();

        StatusBarNotification[] statusBarNotifications = getActiveNotifications(context);
        for (StatusBarNotification statusBarNotification : statusBarNotifications) {

            if (!NotificationLimitManager.isGroupSummary(statusBarNotification)
                    || statusBarNotification.getGroupKey().equals(OneSignalNotificationManager.GROUPLESS_SUMMARY_KEY))
                grouplessStatusBarNotifications.add(statusBarNotification);
        }

        return grouplessStatusBarNotifications;
    }

    /**
     * All groupless notifications are assigned the GROUPLESS_SUMMARY_KEY and notify is called
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    static void assignGrouplessNotifications(Context context, ArrayList<StatusBarNotification> grouplessNotifs, String group) {
        for (StatusBarNotification grouplessNotif : grouplessNotifs) {
            Notification.Builder grouplessNotifBuilder = Notification.Builder.recoverBuilder(context, grouplessNotif.getNotification());
            Notification notif = grouplessNotifBuilder
                    .setGroup(group)
                    .build();
            NotificationManagerCompat.from(context).notify(grouplessNotif.getId(), notif);
        }
    }

}
