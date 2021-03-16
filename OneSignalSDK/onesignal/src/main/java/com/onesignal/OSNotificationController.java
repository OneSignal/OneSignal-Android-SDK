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

import androidx.annotation.Nullable;

import org.json.JSONObject;

import static com.onesignal.OSUtils.isStringNotEmpty;

public class OSNotificationController {

   // The extension service app AndroidManifest.xml meta data tag key name
   private static final String EXTENSION_SERVICE_META_DATA_TAG_NAME = "com.onesignal.NotificationServiceExtension";

   private final OSNotificationGenerationJob notificationJob;
   private boolean restoring;
   private boolean fromBackgroundLogic;

   OSNotificationController(OSNotificationGenerationJob notificationJob, boolean restoring, boolean fromBackgroundLogic) {
      this.restoring = restoring;
      this.fromBackgroundLogic = fromBackgroundLogic;
      this.notificationJob = notificationJob;
   }

   OSNotificationController(Context context, JSONObject jsonPayload, boolean restoring, boolean fromBackgroundLogic, Long timestamp) {
      this.restoring = restoring;
      this.fromBackgroundLogic = fromBackgroundLogic;

      notificationJob = createNotificationJobFromCurrent(context, jsonPayload, timestamp);
   }

   /**
    * Using current {@link OSNotificationController} class attributes, builds a {@link OSNotificationGenerationJob}
    *    instance and returns it
    * <br/><br/>
    * @see OSNotificationGenerationJob
    */
   private OSNotificationGenerationJob createNotificationJobFromCurrent(Context context, JSONObject jsonPayload, Long timestamp) {
      OSNotificationGenerationJob notificationJob = new OSNotificationGenerationJob(context);
      notificationJob.setJsonPayload(jsonPayload);
      notificationJob.setShownTimeStamp(timestamp);
      notificationJob.setRestoring(restoring);
      return notificationJob;
   }

   /**
    * Called from {@link OSNotificationReceivedEvent#complete(OSNotification)} class
    * If the notification modified by the user is null, the notification will be silent, otherwise will be displayed
    * <br/><br/>
    * @param originalNotification the notification received
    * @param notification the notification sent by the user, might be modified
    * @see OSNotificationReceivedEvent#complete(OSNotification)
    */
   void processNotification(OSNotification originalNotification, @Nullable OSNotification notification) {
      if (notification != null) {
         boolean display = isStringNotEmpty(notification.getBody());
         if (!display) {
            // Save as processed to prevent possible duplicate calls from canonical ids
            notDisplayNotificationLogic(originalNotification);
         } else {
            // Set modified notification
            notificationJob.setNotification(notification);
            NotificationBundleProcessor.processJobForDisplay(this, fromBackgroundLogic);
         }
         // Delay to prevent CPU spikes
         // Normally more than one notification is restored at a time
         if (restoring)
            OSUtils.sleep(100);
      } else {
         notDisplayNotificationLogic(originalNotification);
      }
   }

   private void notDisplayNotificationLogic(OSNotification originalNotification) {
      notificationJob.setNotification(originalNotification);
      // Save as processed to prevent possible duplicate calls from canonical ids
      if (restoring) {
         // If we are not displaying a restored notification make sure we mark it as dismissed
         // This will prevent it from being restored again
         NotificationBundleProcessor.markNotificationAsDismissed(notificationJob);
      } else {
         // -1 is used to note never displayed
         notificationJob.getNotification().setAndroidNotificationId(-1);
         NotificationBundleProcessor.processNotification(notificationJob, true, false);
         OneSignal.handleNotificationReceived(notificationJob);
      }
   }

   public OSNotificationGenerationJob getNotificationJob() {
      return notificationJob;
   }

   public OSNotificationReceivedEvent getNotificationReceivedEvent() {
      return new OSNotificationReceivedEvent(this, notificationJob.getNotification());
   }

   public boolean isRestoring() {
      return restoring;
   }

   public void setRestoring(boolean restoring) {
      this.restoring = restoring;
   }

   public boolean isFromBackgroundLogic() {
      return fromBackgroundLogic;
   }

   public void setFromBackgroundLogic(boolean fromBackgroundLogic) {
      this.fromBackgroundLogic = fromBackgroundLogic;
   }

   /**
    * In addition to using the setters to set all of the handlers you can also create your own implementation
    *  within a separate class and give your AndroidManifest.xml a special meta data tag
    * The meta data tag looks like this:
    *  <meta-data android:name="com.onesignal.NotificationServiceExtension" android:value="com.company.ExtensionService" />
    * <br/><br/>
    * There is only one way to implement the {@link OneSignal.OSRemoteNotificationReceivedHandler}
    * <br/><br/>
    * In the case of the {@link OneSignal.OSNotificationWillShowInForegroundHandler}
    *  there are also setters for these handlers. So why create this new class and implement
    *  the same handlers, won't they just overwrite each other?
    * No, the idea here is to keep track of two separate handlers and keep them both
    *  100% optional. The extension handlers are set using the class implementations and the app
    *  handlers set through the setter methods.
    * The extension handlers will always be called first and then bubble to the app handlers
    * <br/><br/>
    * @see OneSignal.OSRemoteNotificationReceivedHandler
    */
   static void setupNotificationServiceExtension(Context context) {
      String className = OSUtils.getManifestMeta(context, EXTENSION_SERVICE_META_DATA_TAG_NAME);

      // No meta data containing extension service class name
      if (className == null) {
         OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "No class found, not setting up OSRemoteNotificationReceivedHandler");
         return;
      }

      OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Found class: " + className + ", attempting to call constructor");
      // Pass an instance of the given class to set any overridden handlers
      try {
         Class<?> clazz = Class.forName(className);
         Object clazzInstance = clazz.newInstance();
         // Make sure a OSRemoteNotificationReceivedHandler exists and remoteNotificationReceivedHandler has not been set yet
         if (clazzInstance instanceof OneSignal.OSRemoteNotificationReceivedHandler && OneSignal.remoteNotificationReceivedHandler == null) {
            OneSignal.setRemoteNotificationReceivedHandler((OneSignal.OSRemoteNotificationReceivedHandler) clazzInstance);
         }
      } catch (IllegalAccessException e) {
         e.printStackTrace();
      } catch (InstantiationException e) {
         e.printStackTrace();
      } catch (ClassNotFoundException e) {
         e.printStackTrace();
      }
   }

   @Override
   public String toString() {
      return "OSNotificationController{" +
              "notificationJob=" + notificationJob +
              ", isRestoring=" + restoring +
              ", isBackgroundLogic=" + fromBackgroundLogic +
              '}';
   }
}