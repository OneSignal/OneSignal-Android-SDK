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

import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

public class OSNotificationExtender {

   // The extension service app AndroidManifest.xml meta data tag key name
   private static final String EXTENSION_SERVICE_META_DATA_TAG_NAME = "com.onesignal.NotificationExtensionServiceClass";

   public static class OverrideSettings {
      public NotificationCompat.Extender extender;
      public Integer androidNotificationId;

      // Note: Make sure future fields are nullable.
      // Possible future options
      //    int badgeCount;
      //   NotificationCompat.Extender summaryExtender;

      void override(OverrideSettings overrideSettings) {
         if (overrideSettings == null)
            return;

         if (overrideSettings.androidNotificationId != null)
            androidNotificationId = overrideSettings.androidNotificationId;

         if (overrideSettings.extender != null)
            extender = overrideSettings.extender;
      }

      @Override
      public String toString() {
         return "OverrideSettings{" +
                 "extender=" + extender +
                 ", androidNotificationId=" + androidNotificationId +
                 '}';
      }
   }

   private Context context;
   private JSONObject jsonPayload;
   private boolean isRestoring;
   private Long timestamp;

   private OverrideSettings currentBaseOverrideSettings;
   OSNotificationDisplayedResult notificationDisplayedResult;

   boolean developerProcessed;

   OSNotificationExtender(Context context, int androidNotificationId, JSONObject jsonPayload, boolean isRestoring, Long timestamp) {
      this.context = context;
      this.jsonPayload = jsonPayload;
      this.isRestoring = isRestoring;
      this.timestamp = timestamp;

      // 0 represents no android notification id being present within the bundle payload so we
      //    should not assign OverrideSettings
      if (androidNotificationId != 0) {
         currentBaseOverrideSettings = new OverrideSettings();
         currentBaseOverrideSettings.androidNotificationId = androidNotificationId;
      }
   }

   /**
    * Called from {@link OSNotificationReceived#setModifiedContent(OverrideSettings)} class since
    *    its responsible for the {@link OSNotificationExtender} class
    * Method call applies any override settings created by the developer within the {@link com.onesignal.OneSignal.NotificationProcessingHandler}
    * <br/><br/>
    * @see OSNotificationReceived#setModifiedContent(OverrideSettings)
    */
   void setModifiedContent(OverrideSettings overrideSettings) {
      if (overrideSettings == null)
         return;

      if (currentBaseOverrideSettings == null)
         currentBaseOverrideSettings = new OverrideSettings();

      currentBaseOverrideSettings.override(overrideSettings);
   }

   /**
    * Called from {@link OSNotificationReceived#display()} class since
    * <br/><br/>
    * @see OSNotificationReceived#display()
    */
   OSNotificationDisplayedResult displayNotification() {
      // Check that the dev called the display method and display result was already created
      if (developerProcessed && notificationDisplayedResult != null)
         return notificationDisplayedResult;

      developerProcessed = true;

      notificationDisplayedResult = new OSNotificationDisplayedResult();

      OSNotificationGenerationJob notificationJob = createnotificationJobFromCurrent(context);
      notificationDisplayedResult.androidNotificationId = NotificationBundleProcessor.processJobForDisplay(notificationJob);

      return notificationDisplayedResult;
   }

   /**
    * Called from {@link OSNotificationReceived#complete()} class since
    * <br/><br/>
    * @see OSNotificationReceived#complete()
    */
   void processNotification() {
      // If the developer did not call display from notificationProcessing handler
      if (!developerProcessed) {

         // Save as processed to prevent possible duplicate calls from canonical ids
         boolean display = NotificationBundleProcessor.shouldDisplay(jsonPayload.optString("alert"));
         if (!display) {
            if (!isRestoring) {
               OSNotificationGenerationJob notificationJob = new OSNotificationGenerationJob(context);
               notificationJob.jsonPayload = jsonPayload;
               notificationJob.overrideSettings = new OverrideSettings();
               notificationJob.overrideSettings.androidNotificationId = -1;

               NotificationBundleProcessor.processNotification(notificationJob, true);
               OneSignal.handleNotificationReceived(notificationJob, false);
            }
            // If are are not displaying a restored notification make sure we mark it as dismissed
            //   This will prevent it from being restored again
            else if (currentBaseOverrideSettings != null)
               NotificationBundleProcessor.markRestoredNotificationAsDismissed(createnotificationJobFromCurrent(context));
         }
         else
            NotificationBundleProcessor.processJobForDisplay(createnotificationJobFromCurrent(context));

         // Delay to prevent CPU spikes
         //    Normally more than one notification is restored at a time
         if (isRestoring)
            OSUtils.sleep(100);
      }
   }

   /**
    * Using current {@link OSNotificationExtender} class attributes, builds a {@link OSNotificationGenerationJob}
    *    instance and returns it
    * <br/><br/>
    * @see OSNotificationGenerationJob
    */
   private OSNotificationGenerationJob createnotificationJobFromCurrent(Context context) {
      OSNotificationGenerationJob notificationJob = new OSNotificationGenerationJob(context);
      notificationJob.isRestoring = isRestoring;
      notificationJob.jsonPayload = jsonPayload;
      notificationJob.shownTimeStamp = timestamp;
      notificationJob.overrideSettings = currentBaseOverrideSettings;

      return notificationJob;
   }

   /**
    * In addition to using the setters to set all of the handlers you can also create your own implementation
    *  within a separate class and give your AndroidManifest.xml a special meta data tag
    * The meta data tag looks like this:
    *  <meta-data android:name="com.onesignal.NotificationExtensionServiceClass" android:value="com.company.ExtensionService" />
    * <br/><br/>
    * There is only one way to implement the {@link OneSignal.NotificationProcessingHandler}
    * <br/><br/>
    * In the case of the {@link OneSignal.ExtNotificationWillShowInForegroundHandler} and the {@link OneSignal.AppNotificationWillShowInForegroundHandler}
    *  there are also setters for these handlers. So why create this new class and implement
    *  the same handlers, won't they just overwrite each other?
    * No, the idea here is to keep track of two separate handlers and keep them both
    *  100% optional. The extension handlers are set using the class implementations and the app
    *  handlers set through the setter methods.
    * The extension handlers will always be called first and then bubble to the app handlers
    * <br/><br/>
    * @see OneSignal.NotificationProcessingHandler
    * @see OneSignal.ExtNotificationWillShowInForegroundHandler
    */
   static void setupNotificationExtensionServiceClass() {
      String className = OSUtils.getManifestMeta(OneSignal.appContext, EXTENSION_SERVICE_META_DATA_TAG_NAME);

      // No meta data containing extension service class name
      if (className == null) {
         OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "No class found, not setting up OSNotificationExtensionService");
         return;
      }

      OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Found class: " + className + ", attempting to call constructor");
      // Pass an instance of the given class to set any overridden handlers
      try {
         Class<?> clazz = Class.forName(className);
         Object clazzInstance = clazz.newInstance();
         // Make sure a NotificationProcessingHandler exists and notificationProcessingHandler has not been set yet
         if (clazzInstance instanceof OneSignal.NotificationProcessingHandler && OneSignal.notificationProcessingHandler == null) {
            OneSignal.setNotificationProcessingHandler((OneSignal.NotificationProcessingHandler) clazzInstance);
         }
         // Make sure a ExtNotificationWillShowInForegroundHandler exists and extNotificationWillShowInForegroundHandler has not been set yet
         if (clazzInstance instanceof OneSignal.ExtNotificationWillShowInForegroundHandler && OneSignal.extNotificationWillShowInForegroundHandler == null) {
            OneSignal.setExtNotificationWillShowInForegroundHandler((OneSignal.ExtNotificationWillShowInForegroundHandler) clazzInstance);
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
      return "OSNotificationExtender{" +
              "context=" + context +
              ", jsonPayload=" + jsonPayload +
              ", isRestoring=" + isRestoring +
              ", timestamp=" + timestamp +
              ", currentBaseOverrideSettings=" + currentBaseOverrideSettings +
              ", notificationDisplayedResult=" + notificationDisplayedResult +
              ", developerProcessed=" + developerProcessed +
              '}';
   }
}