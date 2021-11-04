/**
 * Modified MIT License
 *
 * Copyright 2016 OneSignal
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

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public class OSNotificationReceivedEvent {

   // Timeout time in seconds before auto calling
   private static final long PROCESS_NOTIFICATION_TIMEOUT = 25 * 1_000L;
   private static final String COMPLETE_NOTIFICATION_THREAD = "OS_COMPLETE_NOTIFICATION";

   private final OSNotificationController controller;
   private final OSTimeoutHandler timeoutHandler;
   private final Runnable timeoutRunnable;

   private final OSNotification notification;
   // Used to toggle when complete is called so it can not be called more than once
   private boolean isComplete = false;

   OSNotificationReceivedEvent(OSNotificationController controller, OSNotification notification) {
      this.notification = notification;
      this.controller = controller;

      timeoutHandler = OSTimeoutHandler.getTimeoutHandler();
      timeoutRunnable = new Runnable() {
         @Override
         public void run() {
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Running complete from OSNotificationReceivedEvent timeout runnable!");
            complete(getNotification());
         }
      };
      timeoutHandler.startTimeout(PROCESS_NOTIFICATION_TIMEOUT, timeoutRunnable);
   }

   /**
    * Method to continue with notification processing.
    * User must call complete within 25 seconds or the original notification will be displayed.
    *
    * @param notification can be null to omit displaying the notification,
    *                     or OSMutableNotification to modify the notification to display
    */
   public synchronized void complete(@Nullable final OSNotification notification) {
      timeoutHandler.destroyTimeout(timeoutRunnable);

      if (isComplete) {
         OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "OSNotificationReceivedEvent already completed");
         return;
      }

      isComplete = true;

      if (isRunningOnMainThread()) {
         new Thread(new Runnable() {
            @Override
            public void run() {
               processNotification(notification);
            }
         }, COMPLETE_NOTIFICATION_THREAD).start();
         return;
      }

      processNotification(notification);
   }

   private void processNotification(@Nullable OSNotification notification) {
      // Pass copies to controller, to avoid modifying objects accessed by the user
      controller.processNotification(this.notification.copy(), notification != null ? notification.copy() : null);
   }

   public OSNotification getNotification() {
      return notification;
   }

   public JSONObject toJSONObject() {
      JSONObject mainObj = new JSONObject();

      try {
         mainObj.put("notification", notification.toJSONObject());
         mainObj.put("isComplete", isComplete);
      } catch (JSONException e) {
         e.printStackTrace();
      }

      return mainObj;
   }

   @Override
   public String toString() {
      return "OSNotificationReceivedEvent{" +
              "isComplete=" + isComplete +
              ", notification=" + notification +
              '}';
   }

   static boolean isRunningOnMainThread() {
      return OSUtils.isRunningOnMainThread();
   }
}
