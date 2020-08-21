package com.onesignal.sdktest.notification;

import android.content.Context;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.onesignal.OSNotificationDisplayedResult;
import com.onesignal.OSNotificationExtender;
import com.onesignal.OSNotificationGenerationJob.ExtNotificationGenerationJob;
import com.onesignal.OSNotificationOpenResult;
import com.onesignal.OSNotificationPayload;
import com.onesignal.OSNotificationReceived;
import com.onesignal.OneSignal;
import com.onesignal.sdktest.R;

public class AppNotificationExtensionService implements
        OneSignal.NotificationProcessingHandler,
        OneSignal.ExtNotificationWillShowInForegroundHandler,
        OneSignal.NotificationOpenedHandler {

   @Override
   public void notificationProcessing(Context context, OSNotificationReceived notification) {
      if (notification.payload.actionButtons != null) {
         for (OSNotificationPayload.ActionButton button : notification.payload.actionButtons) {
            System.out.println("button:" + button.toString());
         }
      }

      OSNotificationExtender.OverrideSettings overrideSettings = new OSNotificationExtender.OverrideSettings();
      overrideSettings.extender = new NotificationCompat.Extender() {
         @Override
         public NotificationCompat.Builder extend(NotificationCompat.Builder builder) {
            return builder.setColor(context.getResources().getColor(R.color.colorPrimary));
         }
      };

      notification.setModifiedContent(overrideSettings);

      OSNotificationDisplayedResult notificationDisplayedResult = notification.display();
      Log.i("OneSignal", "Android notification id: " + notificationDisplayedResult.androidNotificationId);

      notification.complete();
   }

   @Override
   public void notificationWillShowInForeground(ExtNotificationGenerationJob notifJob) {
      OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "ExtNotificationWillShowInForeground fired!");

      notifJob.setNotificationDisplayOption(OneSignal.OSNotificationDisplay.NOTIFICATION);
      notifJob.complete(true);
   }

   @Override
   public void notificationOpened(OSNotificationOpenResult result) {

   }

}