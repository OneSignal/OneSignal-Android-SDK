package com.onesignal.example;

import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.onesignal.NotificationExtenderService;
import com.onesignal.OSNotificationDisplayedResult;
import com.onesignal.OSNotificationReceivedResult;

import java.math.BigInteger;

public class NotificationExtenderExample extends NotificationExtenderService {
   @Override
   protected boolean onNotificationProcessing(OSNotificationReceivedResult receivedResult) {
      // Read Properties from result
      OverrideSettings overrideSettings = new OverrideSettings();
      overrideSettings.extender = new NotificationCompat.Extender() {
         @Override
         public NotificationCompat.Builder extend(NotificationCompat.Builder builder) {
            // Sets the background notification color to Red on Android 5.0+ devices.
            return builder.setColor(new BigInteger("FFFF0000", 16).intValue());
         }
      };

      OSNotificationDisplayedResult displayedResult = displayNotification(overrideSettings);
      Log.d("OneSignalExample", "Notification displayed with id: " + displayedResult.androidNotificationId);

      // Return true to stop the notification from displaying
      return true;
   }
}
