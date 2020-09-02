package com.onesignal.sdktest.notification;

import android.content.Context;

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
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "ActionButton: " + button.toString());
         }
      }

      OSNotificationExtender.OverrideSettings overrideSettings = new OSNotificationExtender.OverrideSettings();
      overrideSettings.extender = builder -> builder.setColor(context.getResources().getColor(R.color.colorPrimary));

      notification.setModifiedContent(overrideSettings);

      // If Developer doesn't call notification.display() ExtNotificationWillShowInForegroundHandler
      // and AppNotificationWillShowInForegroundHandler won't be call
      OSNotificationDisplayedResult notificationDisplayedResult = notification.display();
      OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Android notification id: " + notificationDisplayedResult.androidNotificationId);

      // If complete isn't call and notification.display() was called, after 5 seconds
      // OneSignal will continue calling ExtNotificationWillShowInForegroundHandler
      notification.complete();
   }

   @Override
   public void notificationWillShowInForeground(ExtNotificationGenerationJob notificationJob) {
      OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "ExtNotificationWillShowInForeground fired! " +
              "with ExtNotificationGenerationJob: " + notificationJob.toString());

      notificationJob.setNotificationDisplayOption(OneSignal.OSNotificationDisplay.NOTIFICATION);

      // If bubble set to false AppNotificationWillShowInForegroundHandler won't be called
      notificationJob.complete(true);
   }

   @Override
   public void notificationOpened(OSNotificationOpenResult result) {
      OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "OSNotificationOpenResult result: " + result.toString());
   }
}
