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

import android.content.Context;

import com.onesignal.OSNotificationExtender.OverrideSettings;

import org.json.JSONObject;

public class OSNotificationReceived {

   // Timeout in seconds before auto calling
   private static final long PROCESS_NOTIFICATION_TIMEOUT = 5 * 1_000L;

   private final OSTimeoutHandler timeoutHandler;
   private Runnable timeoutRunnable;
   private OSNotificationExtender notificationExtender;
   private OSNotificationPayload payload;
   private boolean isRestoring;
   private boolean isAppInFocus;
   // Used to toggle when complete is called so it can not be called more than once
   private boolean isComplete = false;
   // Flag that differentiate user custom flow from OneSignal
   private boolean internalComplete = false;

   OSNotificationReceived(Context context, int androidNotificationId, JSONObject jsonPayload,
                          boolean isRestoring, boolean isAppInFocus, long timestamp) {
      this.payload = NotificationBundleProcessor.OSNotificationPayloadFrom(jsonPayload);
      this.isRestoring = isRestoring;
      this.isAppInFocus = isAppInFocus;

      timeoutHandler = OSTimeoutHandler.getTimeoutHandler();
      notificationExtender = new OSNotificationExtender(
              context,
              androidNotificationId,
              jsonPayload,
              isRestoring,
              timestamp
      );

      timeoutRunnable = new Runnable() {
         @Override
         public void run() {
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Running complete from OSNotificationReceived timeout runnable!");
            complete();
         }
      };
      timeoutHandler.startTimeout(PROCESS_NOTIFICATION_TIMEOUT, timeoutRunnable);
   }

   private boolean isDeveloperProcessed() {
      return notificationExtender.developerProcessed;
   }

   private boolean hasNotificationDisplayedResult() {
      return notificationExtender.notificationDisplayedResult != null;
   }

   boolean displayed() {
      return isDeveloperProcessed() && hasNotificationDisplayedResult();
   }

   /**
    * If a developer wants to override the data within a received notification, they can do so by
    *    creating a {@link androidx.core.app.NotificationCompat.Extender} within the {@link com.onesignal.OneSignal.NotificationProcessingHandler}
    *    and override any notification data desired
    * This notification data will be given to a {@link OSNotificationExtender} class and then passed
    *    into the {@link OSNotificationReceived#setModifiedContent(OverrideSettings)}
    * <br/><br/>
    * @see com.onesignal.OneSignal.NotificationProcessingHandler
    */
   public synchronized void setModifiedContent(OverrideSettings overrideSettings) {
      notificationExtender.setModifiedContent(overrideSettings);
   }

   /**
    * If a developer wants to display the notification, whether or not {@link OSNotificationReceived#setModifiedContent(OverrideSettings)}
    *    was called by the developer
    * Without calling this method, the notification will not display at all
    * <br/><br/>
    * Returns a {@link OSNotificationDisplayedResult}, which currently is only responsible for the
    *    android notification id
    * <br/><br/>
    * @see OSNotificationDisplayedResult
    * @see com.onesignal.OneSignal.NotificationProcessingHandler
    */
   public synchronized OSNotificationDisplayedResult display() {
      return notificationExtender.displayNotification();
   }

   public boolean isRestoring() {
      return isRestoring;
   }

   public boolean isAppInFocus() {
      return isAppInFocus;
   }

   public OSNotificationPayload getPayload() {
      return payload;
   }

   synchronized void internalComplete() {
      internalComplete = true;
      complete();
   }

   /**
    * Method controlling completion from the NotificationProcessingHandler
    * If a dev does not call this at the end of the notificationProcessing implementation,
    *  a runnable will fire after a 5 second timer and complete by default
    */
   public synchronized void complete() {
      timeoutHandler.destroyTimeout(timeoutRunnable);

      if (isComplete)
         return;

      isComplete = true;

      notificationExtender.processNotification(internalComplete);
   }

   @Override
   public String toString() {
      return "OSNotificationReceived{" +
              "notificationExtender=" + notificationExtender +
              ", payload=" + payload +
              ", isRestoring=" + isRestoring +
              ", isAppInFocus=" + isAppInFocus +
              ", isComplete=" + isComplete +
              '}';
   }

   public JSONObject toJSONObject() {
      JSONObject mainObj = new JSONObject();

      try {
         mainObj.put("restoring", isRestoring);
         mainObj.put("appInFocus", isAppInFocus);
         mainObj.put("completed", isComplete);
         mainObj.put("payload", payload.toJSONObject());
      }
      catch(Throwable t) {
         t.printStackTrace();
      }

      return mainObj;
   }
}