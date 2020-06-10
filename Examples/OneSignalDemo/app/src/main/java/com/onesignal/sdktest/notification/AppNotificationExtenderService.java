package com.onesignal.sdktest.notification;

import android.content.Context;

import androidx.core.app.NotificationCompat;

import com.onesignal.NotificationExtender;
import com.onesignal.OSNotificationGenerationJob.ExtNotificationGenerationJob;
import com.onesignal.OSNotificationOpenResult;
import com.onesignal.OSNotificationPayload;
import com.onesignal.OSNotificationReceived;
import com.onesignal.OSNotificationReceivedResult;
import com.onesignal.OneSignal;
import com.onesignal.sdktest.R;

public class AppNotificationExtenderService implements
        OneSignal.NotificationProcessingHandler,
        OneSignal.ExtNotificationWillShowInForegroundHandler,
        OneSignal.NotificationOpenedHandler {

   @Override
   public void onNotificationProcessing(Context context, OSNotificationReceived notification) {
      OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "onNotificationProcessing fired!!!");
      if (notification.payload.actionButtons != null) {
         for (OSNotificationPayload.ActionButton button : notification.payload.actionButtons) {
            System.out.println("button:" + button.toString());
         }
      }

      NotificationExtender.OverrideSettings overrideSettings = new NotificationExtender.OverrideSettings();
      overrideSettings.extender = new NotificationCompat.Extender() {
         @Override
         public NotificationCompat.Builder extend(NotificationCompat.Builder builder) {
            return builder.setColor(context.getResources().getColor(R.color.colorPrimary));
         }
      };

      notification.setModifiedContent(overrideSettings);
      OSNotificationReceivedResult notificationReceivedResult = notification.display();

      OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "onNotificationProcessing finished wth Android notification id: " + notificationReceivedResult.androidNotificationId);

      notification.complete();
   }

   @Override
   public void notificationWillShowInForeground(ExtNotificationGenerationJob notifJob) {
      OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Ext notificationWillShowInForeground fired!!!!");

      notifJob.setNotificationDisplayOption(OneSignal.OSNotificationDisplay.NOTIFICATION);
      notifJob.complete(true);
   }

   @Override
   public void notificationOpened(OSNotificationOpenResult result) {

   }

}

