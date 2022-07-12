package com.onesignal.usersdktest.notification;

import android.content.Context;

import com.onesignal.onesignal.core.internal.logging.LogLevel;
import com.onesignal.onesignal.core.internal.logging.Logging;
import com.onesignal.onesignal.notification.IActionButton;
import com.onesignal.onesignal.notification.IMutableNotification;
import com.onesignal.onesignal.notification.INotification;
import com.onesignal.onesignal.notification.INotificationReceivedEvent;
import com.onesignal.onesignal.notification.IRemoteNotificationReceivedHandler;
import com.onesignal.usersdktest.R;

public class NotificationServiceExtension implements IRemoteNotificationReceivedHandler {

   @Override
   public void remoteNotificationReceived(Context context, INotificationReceivedEvent notificationReceivedEvent) {
      Logging.log(LogLevel.VERBOSE, "OSRemoteNotificationReceivedHandler fired!" +
              " with OSNotificationReceived: " + notificationReceivedEvent.toString());

      INotification notification = notificationReceivedEvent.getNotification();

      if (notification.getActionButtons() != null) {
         for (IActionButton button : notification.getActionButtons()) {
            Logging.log(LogLevel.VERBOSE, "ActionButton: " + button.toString());
         }
      }

      IMutableNotification mutableNotification = notification.mutableCopy();
      mutableNotification.setExtender(builder -> builder.setColor(context.getResources().getColor(R.color.colorPrimary)));

      // If complete isn't call within a time period of 25 seconds, OneSignal internal logic will show the original notification
      notificationReceivedEvent.complete(mutableNotification);
   }
}
