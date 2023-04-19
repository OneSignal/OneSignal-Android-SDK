package com.onesignal.sdktest.notification;

import android.util.Log;

import com.onesignal.notifications.IActionButton;
import com.onesignal.notifications.IDisplayableMutableNotification;
import com.onesignal.notifications.INotificationReceivedEvent;
import com.onesignal.notifications.INotificationServiceExtension;
import com.onesignal.sdktest.R;

public class NotificationServiceExtension implements INotificationServiceExtension {

   @Override
   public void onNotificationReceived(INotificationReceivedEvent event) {
      Log.v("MainApplication", "IRemoteNotificationReceivedHandler fired!" + " with INotificationReceivedEvent: " + event.toString());

      IDisplayableMutableNotification notification = event.getNotification();

      if (notification.getActionButtons() != null) {
         for (IActionButton button : notification.getActionButtons()) {
            Log.v("MainApplication", "ActionButton: " + button.toString());
         }
      }

      notification.setExtender(builder -> builder.setColor(event.getContext().getResources().getColor(R.color.colorPrimary)));
   }
}
