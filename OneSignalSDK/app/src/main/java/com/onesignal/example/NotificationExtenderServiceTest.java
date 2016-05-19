package com.onesignal.example;

import android.support.v4.app.NotificationCompat;

import com.onesignal.OSNotificationPayload;
import com.onesignal.NotificationExtenderService;

import java.lang.reflect.Field;
import java.math.BigInteger;

public class NotificationExtenderServiceTest extends NotificationExtenderService {

   @Override
   protected boolean onNotificationProcessing(OSNotificationPayload notification) {
      printObject(notification);
      if (notification.actionButtons != null) {
         for(OSNotificationPayload.ActionButton button : notification.actionButtons) {
            System.out.println("button:");
            printObject(button);
         }
      }

      OverrideSettings overrideSettings = new OverrideSettings();

      overrideSettings.extender = new NotificationCompat.Extender() {
         @Override
         public NotificationCompat.Builder extend(NotificationCompat.Builder builder) {
            return builder.setColor(new BigInteger("FF00FF00", 16).intValue());
         }
      };

      displayNotification(overrideSettings);

      return true;
   }

   private void printObject(Object obj) {
      for (Field field : obj.getClass().getDeclaredFields()) {
         String name = field.getName();
         try {
            Object value = field.get(obj);
            System.out.printf("Field name: %s, Field value: %s%n", name, value);
         } catch (Throwable t){}
      }
   }
}
