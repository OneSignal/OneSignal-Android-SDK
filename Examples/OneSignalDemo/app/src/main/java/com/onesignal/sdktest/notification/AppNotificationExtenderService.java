package com.onesignal.sdktest.notification;

import android.support.v4.app.NotificationCompat;

import com.onesignal.NotificationExtenderService;
import com.onesignal.OSNotificationDisplayedResult;
import com.onesignal.OSNotificationPayload;
import com.onesignal.OSNotificationReceivedResult;
import com.onesignal.OneSignal;
import com.onesignal.sdktest.R;

public class AppNotificationExtenderService extends NotificationExtenderService {

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

      OSNotificationDisplayedResult displayedResult = modifyNotification(overrideSettings);
      if (displayedResult != null)
         OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Notification displayed with id: " + displayedResult.androidNotificationId);

      return true;
   }

}