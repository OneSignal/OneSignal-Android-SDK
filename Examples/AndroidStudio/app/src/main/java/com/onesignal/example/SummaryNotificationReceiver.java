package com.onesignal.example;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.RequiresApi;

import java.util.TreeMap;

public class SummaryNotificationReceiver extends BroadcastReceiver {

   @RequiresApi(api = Build.VERSION_CODES.M)
   @Override
   public void onReceive(Context context, Intent intent) {
      TreeMap<Long, NotificationUtils.ChildNotification> activeChildNotifs = NotificationUtils.getTreeMapOfChildNotifications(context);
      if (activeChildNotifs.size() == 0)
         return;

      try {
         // Remove the most recent child notification from the shade and fires it's intent
         NotificationUtils.ChildNotification mostRecentNotif = activeChildNotifs.firstEntry().getValue();
         NotificationUtils.cancelNotification(context, mostRecentNotif.id);
         mostRecentNotif.contentIntent.send();
      } catch (PendingIntent.CanceledException e) {
         e.printStackTrace();
      }
   }
}
