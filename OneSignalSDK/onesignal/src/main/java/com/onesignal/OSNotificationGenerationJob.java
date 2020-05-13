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


import android.content.Context;
import android.net.Uri;
import android.os.Handler;

import com.onesignal.OneSignal.OSNotificationDisplay;

import org.json.JSONObject;

import java.security.SecureRandom;

public class OSNotificationGenerationJob {

   Context context;
   JSONObject jsonPayload;
   boolean isRestoring;
   boolean isIamPreview;
   OSNotificationDisplay displayOption = OSNotificationDisplay.NOTIFICATION;

   Long shownTimeStamp;

   CharSequence overriddenBodyFromExtender;
   CharSequence overriddenTitleFromExtender;
   Uri overriddenSound;
   Integer overriddenFlags;
   Integer orgFlags;
   Uri orgSound;

   NotificationExtenderService.OverrideSettings overrideSettings;

   OSNotificationGenerationJob(Context context) {
      this.context = context;
   }

   CharSequence getTitle() {
      if (overriddenTitleFromExtender != null)
         return overriddenTitleFromExtender;
      return jsonPayload.optString("title", null);
   }
   
   CharSequence getBody() {
      if (overriddenBodyFromExtender != null)
         return overriddenBodyFromExtender;
      return jsonPayload.optString("alert", null);
   }
   
   Integer getAndroidId() {
      if (overrideSettings == null)
         overrideSettings = new NotificationExtenderService.OverrideSettings();
      if (overrideSettings.androidNotificationId == null)
         overrideSettings.androidNotificationId = new SecureRandom().nextInt();
      
      return overrideSettings.androidNotificationId;
   }

   int getAndroidIdWithoutCreate() {
      if (overrideSettings == null || overrideSettings.androidNotificationId == null)
         return -1;

      return overrideSettings.androidNotificationId;
   }

   /**
    * If notificationId is -1 then the notification is a silent one
    */
   boolean isNotificationToDisplay() {
      return getAndroidIdWithoutCreate() != -1;
   }

   String getApiNotificationId() {
      return OneSignal.getNotificationIdFromFCMJson(jsonPayload);
   }

   void setAndroidIdWithOutOverriding(Integer id) {
      if (id == null)
         return;

      if (overrideSettings != null && overrideSettings.androidNotificationId != null)
         return;

      if (overrideSettings == null)
         overrideSettings = new NotificationExtenderService.OverrideSettings();
      overrideSettings.androidNotificationId = id;
   }

   boolean hasExtender() {
      return overrideSettings != null && overrideSettings.extender != null;
   }

   private void setNotificationDisplayOption(OSNotificationDisplay displayOption) {
      this.displayOption = displayOption;
   }

   ExtNotificationGenerationJob toExtNotificationGenerationJob() {
      return new ExtNotificationGenerationJob(this);
   }

   AppNotificationGenerationJob toAppNotificationGenerationJob() {
      return new AppNotificationGenerationJob(this);
   }

   /**
    * A wrapper for the {@link OSNotificationGenerationJob}
    * Contains two other classes which implement this one {@link NotificationGenerationJob}:
    *    1. {@link ExtNotificationGenerationJob}
    *    2. {@link AppNotificationGenerationJob}
    */
   private static class NotificationGenerationJob {

      // Timeout in seconds before applying defaults
      private static final long SHOW_NOTIFICATION_TIMEOUT = 30 * 1_000L;

      // The actual notifJob with notification payload data
      private OSNotificationGenerationJob notifJob;

      // Handler used to timeout the handler if bubble or complete is not called
      private Handler timeoutHandler;

      NotificationGenerationJob(OSNotificationGenerationJob notifJob) {
         this.notifJob = notifJob;
      }

      void startShowNotificationTimeout(final Runnable timeoutRunnable) {
         OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
               timeoutHandler = new Handler();
               timeoutHandler.postDelayed(timeoutRunnable, SHOW_NOTIFICATION_TIMEOUT);
            }
         });
      }

      void destroyShowNotificationTimeout() {
         if (timeoutHandler != null)
            timeoutHandler.removeCallbacks(null);

         timeoutHandler = null;
      }

      public OSNotificationGenerationJob getNotifJob() {
         return notifJob;
      }

      public String getBody() {
         return notifJob.getBody().toString();
      }

      public int getAndroidNotificationId() {
         return notifJob.getAndroidIdWithoutCreate();
      }

      public void setNotificationDisplayOption(OSNotificationDisplay displayOption) {
         notifJob.setNotificationDisplayOption(displayOption);
      }
   }

   /**
    * Used to modify the {@link OSNotificationGenerationJob} inside of the {@link OneSignal.ExtNotificationWillShowInForegroundHandler}
    *    without exposing internals publically
    */
   public static class ExtNotificationGenerationJob extends NotificationGenerationJob {

      ExtNotificationGenerationJob(OSNotificationGenerationJob notifJob) {
         super(notifJob);

         startShowNotificationTimeout(new Runnable() {
            @Override
            public void run() {
               new Thread(new Runnable() {
                  @Override
                  public void run() {
                     ExtNotificationGenerationJob.this.bubble(true);
                  }
               }).start();
            }
         });
      }

      // Method controlling bubbling from the ExtNotificationWillShowInForegroundHandler to the AppNotificationWillShowInForegroundHandler
      //    If a dev does not call this at the end of the notificationWillShowInForeground implementation, a runnable will fire after
      //    a 30 second timer and attempt to bubble to the AppNotificationWillShowInForegroundHandler automatically
      public void bubble(boolean bubble) {
         destroyShowNotificationTimeout();

         // Move on to showing notification if no AppNotificationWillShowInForegroundHandler exists or bubbling is set false
         if (OneSignal.appNotificationWillShowInForegroundHandler == null || !bubble) {
            NotificationExtenderService.showNotification(getNotifJob());
            return;
         }

         // If the appNotificationWillShowInForegroundHandler exists and we want to bubble, call
         //    the notificationWillShowInForeground implementation
         OneSignal.appNotificationWillShowInForegroundHandler.notificationWillShowInForeground(
                 getNotifJob().toAppNotificationGenerationJob());
      }
   }

   /**
    * Used to modify the {@link OSNotificationGenerationJob} inside of the {@link OneSignal.AppNotificationWillShowInForegroundHandler}
    *    without exposing internals publically
    */
   public static class AppNotificationGenerationJob extends NotificationGenerationJob {

      AppNotificationGenerationJob(OSNotificationGenerationJob notifJob) {
         super(notifJob);

         startShowNotificationTimeout(new Runnable() {
            @Override
            public void run() {
               new Thread(new Runnable() {
                  @Override
                  public void run() {
                     AppNotificationGenerationJob.this.complete();
                  }
               }).start();
            }
         });
      }

      // Method controlling completion from the AppNotificationWillShowInForegroundHandler
      //    If a dev does not call this at the end of the notificationWillShowInForeground implementation, a runnable will fire after
      //    a 30 second timer and complete by default
      public void complete() {
         destroyShowNotificationTimeout();
         NotificationExtenderService.showNotification(getNotifJob());
      }
   }

}
