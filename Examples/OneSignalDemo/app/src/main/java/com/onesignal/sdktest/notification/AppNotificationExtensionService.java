package com.onesignal.sdktest.notification;

import android.content.Context;

import com.onesignal.OSNotificationDisplayedResult;
import com.onesignal.OSNotificationExtender;
import com.onesignal.OSNotificationOpenResult;
import com.onesignal.OSNotificationPayload;
import com.onesignal.OSNotificationReceived;
import com.onesignal.OneSignal;
import com.onesignal.sdktest.R;

public class AppNotificationExtensionService implements
        OneSignal.NotificationProcessingHandler,
        OneSignal.NotificationOpenedHandler {

   @Override
   public void notificationProcessing(Context context, OSNotificationReceived notification) {
      OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "NotificationProcessingHandler fired!" +
              " with OSNotificationReceived: " + notification.toString());
      if (notification.getPayload().getActionButtons() != null) {
         for (OSNotificationPayload.ActionButton button : notification.getPayload().getActionButtons()) {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "ActionButton: " + button.toString());
         }
      }

      OSNotificationExtender.OverrideSettings overrideSettings = new OSNotificationExtender.OverrideSettings();
      overrideSettings.setExtender(builder -> builder.setColor(context.getResources().getColor(R.color.colorPrimary)));

      notification.setModifiedContent(overrideSettings);

      // If Developer doesn't call notification.display()
      // NotificationWillShowInForegroundHandler won't be call
      OSNotificationDisplayedResult notificationDisplayedResult = notification.display();
      OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Android notification id: " + notificationDisplayedResult.getAndroidNotificationId());

      // If complete isn't call and notification.display() was called, after 5 seconds
      // OneSignal will continue calling NotificationWillShowInForegroundHandler
      notification.complete();
   }

   @Override
   public void notificationOpened(OSNotificationOpenResult result) {
      OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "OSNotificationOpenResult result: " + result.toString());
   }
}
