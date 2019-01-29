package com.onesignal.example;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.onesignal.NotificationExtenderService;
import com.onesignal.OSNotificationReceivedResult;

import java.math.BigInteger;

public class NotificationExtenderExample extends NotificationExtenderService {

   private static String NOTIFICATION_CHANNEL = "fcm_fallback_notification_channel";
   private static String NOTIFICATION_GROUP_KEY = "test";
   static int NOTIFICATION_GROUP_ID = 1234;

   @Override
   protected boolean onNotificationProcessing(OSNotificationReceivedResult receivedResult) {
      OverrideSettings overrideSettings = new OverrideSettings();
      overrideSettings.extender = new NotificationCompat.Extender() {
         @Override
         public NotificationCompat.Builder extend(NotificationCompat.Builder builder) {
            return builder.setGroup(NOTIFICATION_GROUP_KEY);
         }
      };

      displayNotification(overrideSettings);
      displaySummaryNotification();

      // Return true to stop the notification from displaying
      return true;
   }

   private void displaySummaryNotification() {
      // Android pre-7.0 would keep notifications individual, however on newer versions they auto group.
      //   The auto grouping behavior isn't desirable since it does not put any data on the intent.
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
         return;

      Intent intent = new Intent(this, SummaryNotificationReceiver.class);
      PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);

      NotificationCompat.Builder summmaryBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL);
      summmaryBuilder
         .setColor(new BigInteger("FFFF0000", 16).intValue())
         .setSmallIcon(android.R.drawable.ic_popup_reminder)
         .setContentText("This Summary Text should NOT be seen.")
         .setGroup(NOTIFICATION_GROUP_KEY)
         .setContentIntent(pendingIntent)
         .setGroupSummary(true);

      NotificationManagerCompat.from(this).notify(NOTIFICATION_GROUP_ID, summmaryBuilder.build());
   }
}
