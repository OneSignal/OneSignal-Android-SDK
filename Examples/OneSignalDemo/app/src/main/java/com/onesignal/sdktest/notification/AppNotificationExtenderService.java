package com.onesignal.sdktest.notification;

import android.support.v4.app.NotificationCompat;

import com.onesignal.NotificationExtenderService;
import com.onesignal.OSNotification;
import com.onesignal.OSNotificationOpenResult;
import com.onesignal.OSNotificationPayload;
import com.onesignal.OSNotificationReceivedResult;
import com.onesignal.OneSignal;
import com.onesignal.sdktest.R;

public class AppNotificationExtenderService extends NotificationExtenderService implements OneSignal.NotificationReceivedHandler, OneSignal.NotificationOpenedHandler {

   @Override
   protected boolean onNotificationProcessing(OSNotificationReceivedResult notification) {
      if (notification.payload.actionButtons != null) {
         for (OSNotificationPayload.ActionButton button : notification.payload.actionButtons) {
            System.out.println("button:" + button.toString());
         }
      }

      OverrideSettings overrideSettings = new NotificationExtenderService.OverrideSettings();
      overrideSettings.extender = new NotificationCompat.Extender() {
         @Override
         public NotificationCompat.Builder extend(NotificationCompat.Builder builder) {
            return builder.setColor(getResources().getColor(R.color.colorPrimary));
         }
      };

      displayNotification(overrideSettings);

      return true;
   }

   @Override
   public void notificationReceived(OSNotification notification) {

   }

   @Override
   public void notificationOpened(OSNotificationOpenResult result) {

   }

}