package com.onesignal;

import android.app.Notification;
import android.app.NotificationManager;

import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowNotification;
import org.robolectric.shadows.ShadowNotificationManager;

import java.util.ArrayList;
import java.util.List;

import static org.robolectric.Shadows.shadowOf;

@Implements(NotificationManager.class)
public class ShadowRoboNotificationManager extends ShadowNotificationManager {

   public class PostedNotification {

      PostedNotification(int id, ShadowNotification notif) {
         this.id = id; this.notif = notif;
      }

      public int id;
      public ShadowNotification notif;
   }

   public static ShadowNotification lastNotif;
   public static int lastNotifId;

   public static List<PostedNotification> notifications = new ArrayList<PostedNotification>();

   @Override
   public void notify(String tag, int id, Notification notification) {
      lastNotif = shadowOf(notification);
      lastNotifId = id;
      notifications.add(new PostedNotification(id, lastNotif));
      System.out.println("notification: " + lastNotif.getContentText());
      super.notify(tag, id, notification);
      //throw new RuntimeException("Get stack trace!");
   }

}