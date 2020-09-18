package com.onesignal.sdktest.notification;

import android.content.Context;

import com.onesignal.OSMutableNotification;
import com.onesignal.OSNotification;
import com.onesignal.OSNotificationOpenedResult;
import com.onesignal.OSNotificationReceivedEvent;
import com.onesignal.OneSignal;
import com.onesignal.sdktest.R;

public class AppRemoteNotificationExtensionService implements
        OneSignal.OSRemoteNotificationReceivedHandler,
        OneSignal.OSNotificationOpenedHandler {

   @Override
   public void remoteNotificationReceived(Context context, OSNotificationReceivedEvent notificationReceivedEvent) {
      OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "NotificationProcessingHandler fired!" +
              " with OSNotificationReceived: " + notificationReceivedEvent.toString());

      OSNotification notification = notificationReceivedEvent.getNotification();

      if (notification.getActionButtons() != null) {
         for (OSNotification.ActionButton button : notification.getActionButtons()) {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "ActionButton: " + button.toString());
         }
      }

      OSMutableNotification mutableNotification = notification.mutableCopy();
      mutableNotification.setExtender(builder -> builder.setColor(context.getResources().getColor(R.color.colorPrimary)));

      // If complete isn't call within a time period of 25 seconds, OneSignal internal logic will show the original notification
      notificationReceivedEvent.complete(mutableNotification);
   }

   @Override
   public void notificationOpened(OSNotificationOpenedResult result) {
      OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "OSNotificationOpenResult result: " + result.toString());
   }
}
