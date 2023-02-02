package com.onesignal.sdktest.notification;

import android.content.Context;
import android.util.Log;

import com.onesignal.notifications.IActionButton;
import com.onesignal.notifications.IMutableNotification;
import com.onesignal.notifications.INotification;
import com.onesignal.notifications.INotificationReceivedEvent;
import com.onesignal.notifications.IRemoteNotificationReceivedHandler;
import com.onesignal.sdktest.R;

public class NotificationServiceExtension implements IRemoteNotificationReceivedHandler {

   @Override
   public void remoteNotificationReceived(Context context, INotificationReceivedEvent notificationReceivedEvent) {
      Log.v("MainApplication", "IRemoteNotificationReceivedHandler fired!" + " with INotificationReceivedEvent: " + notificationReceivedEvent.toString());

      INotification notification = notificationReceivedEvent.getNotification();

      if (notification.getActionButtons() != null) {
         for (IActionButton button : notification.getActionButtons()) {
            Log.v("MainApplication", "ActionButton: " + button.toString());
         }
      }

      IMutableNotification mutableNotification = notification.mutableCopy();
      mutableNotification.setExtender(builder -> builder.setColor(context.getResources().getColor(R.color.colorPrimary)));

      // If complete isn't call within a time period of 25 seconds, OneSignal internal logic will show the original notification
      notificationReceivedEvent.complete(mutableNotification);
   }
}
