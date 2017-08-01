/**
 * Modified MIT License
 *
 * Copyright 2017 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.onesignal;

import android.app.NotificationChannel;
import android.app.Notification;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;

import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowNotification;
import org.robolectric.shadows.ShadowNotificationManager;

import java.util.LinkedHashMap;

import static org.robolectric.Shadows.shadowOf;

@Implements(value = NotificationManager.class, looseSignatures = true)
public class ShadowRoboNotificationManager extends ShadowNotificationManager {

   public class PostedNotification {

      PostedNotification(int id, Notification notif) {
         this.id = id; this.notif = notif;
      }

      public int id;
      public Notification notif;

      public ShadowNotification getShadow() {
         return shadowOf(notif);
      }
   }

   private static Notification lastNotif;
   public static ShadowNotification getLastShadowNotif() {
      return shadowOf(lastNotif);
   }

   public static int lastNotifId;

   public static LinkedHashMap<Integer, PostedNotification> notifications = new LinkedHashMap<>();
   
   @Override
   public void cancelAll() {
      super.cancelAll();
      notifications.clear();
   }
   
   @Override
   public void cancel(int id) {
      super.cancel(id);
      notifications.remove(id);
   }
   
   @Override
   public void cancel(String tag, int id) {
      super.cancel(tag, id);
      notifications.remove(id);
   }

   @Override
   public void notify(String tag, int id, Notification notification) {
      lastNotif = notification;
      lastNotifId = id;
      notifications.put(id, new PostedNotification(id, lastNotif));
      super.notify(tag, id, notification);
   }

   public static NotificationChannel lastChannel;
   public void createNotificationChannel(NotificationChannel channel) {
      lastChannel = channel;
      super.createNotificationChannel((Object)channel);
   }

   public static NotificationChannelGroup lastChannelGroup;
   public void createNotificationChannelGroup(NotificationChannelGroup group) {
      lastChannelGroup = group;
      super.createNotificationChannelGroup((Object)group);
   }
}