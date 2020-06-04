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
import android.content.Intent;
import android.os.Bundle;
import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;

// Designed to be extended by app developer.
// Must add the following to the AndroidManifest.xml for this to be triggered.

/*
<service
   android:name=".NotificationExtenderServiceExample"
   android:exported="false">
   <intent-filter>
      <action android:name="com.onesignal.NotificationExtender" />
   </intent-filter>
</service>
*/

/**
 * OneSignal supports sending additional data along with a notification as key/value pairs.
 * You can read this additional data when a notification is opened by adding a
 * {@link com.onesignal.OneSignal.NotificationOpenedHandler} instead.
 * <br/><br/>
 * However, if you want to do one of the following, continue with the instructions
 * <a href="https://documentation.onesignal.com/docs/android-native-sdk#section--notificationextenderservice-">here</a>:
 * <br/><br/>
 * - Receive data in the background with our without displaying a notification
 * <br/>
 * - Override specific notification settings depending on client-side app logic (e.g. custom accent color,
 * vibration pattern, any other {@link NotificationCompat} options)
 */
public class NotificationExtenderService {

   static final int EXTENDER_SERVICE_JOB_ID = 2071862121;

   // The extension service app AndroidManifest.xml meta data tag key name
   private static final String EXTENSION_SERVICE_META_DATA_TAG_NAME = "com.onesignal.NotificationExtensionServiceClass";

   private static NotificationExtenderService mService;

   public static NotificationExtenderService getInstance() {
      if (mService == null)
         mService = new NotificationExtenderService();

      return mService;
   }

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
      }
   }

   private OSNotificationDisplayedResult notificationReceivedResult;
   private JSONObject currentJsonPayload;
   private boolean currentlyRestoring;
   private Long restoreTimestamp;
   private OverrideSettings currentBaseOverrideSettings = null;

   // Developer may call to override some notification settings.
   // If this method is called the SDK will omit it's notification regardless of what is returned from onNotificationProcessing.
   protected final void displayNotification(Context context, OverrideSettings overrideSettings) {
      // Check if this method has been called already or if no override was set.
      if (notificationReceivedResult != null || overrideSettings == null)
         return;

      overrideSettings.override(currentBaseOverrideSettings);
      notificationReceivedResult = new OSNotificationDisplayedResult();

      OSNotificationGenerationJob notifJob = createNotifJobFromCurrent(context);
      notifJob.overrideSettings = overrideSettings;

      notificationReceivedResult.androidNotificationId = NotificationBundleProcessor.ProcessJobForDisplay(notifJob);
   }

   void processIntent(Context context, Intent intent) {
      Bundle bundle = intent.getExtras();

      // Service maybe triggered without extras on some Android devices on boot.
      // https://github.com/OneSignal/OneSignal-Android-SDK/issues/99
      if (bundle == null) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "No extras sent to NotificationExtenderService in its Intent!\n" + intent);
         return;
      }

      String jsonStrPayload = bundle.getString("json_payload");
      if (jsonStrPayload == null) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "json_payload key is nonexistent from bundle passed to NotificationExtenderService: " + bundle);
         return;
      }

      try {
         currentJsonPayload = new JSONObject(jsonStrPayload);
         currentlyRestoring = bundle.getBoolean("restoring", false);
         if (bundle.containsKey("android_notif_id")) {
            currentBaseOverrideSettings = new OverrideSettings();
            currentBaseOverrideSettings.androidNotificationId = bundle.getInt("android_notif_id");
         }

         if (!currentlyRestoring && OneSignal.notValidOrDuplicated(context, currentJsonPayload))
            return;

         restoreTimestamp = bundle.getLong("timestamp");
         processJsonObject(context, currentJsonPayload, currentlyRestoring);
      } catch (JSONException e) {
         e.printStackTrace();
      }
   }

   void processJsonObject(Context context, JSONObject currentJsonPayload, boolean restoring) {
      OSNotificationReceived receivedResult = new OSNotificationReceived(
              NotificationBundleProcessor.OSNotificationPayloadFrom(currentJsonPayload),
              restoring,
              OneSignal.isAppActive());

      notificationReceivedResult = null;
      boolean developerProcessed = OneSignal.notificationProcessingHandler != null;
      try {
         if (developerProcessed)
            OneSignal.notificationProcessingHandler.onNotificationProcessing(context, receivedResult);
      } catch (Throwable t) {
         //noinspection ConstantConditions - displayNotification might have been called by the developer
         if (notificationReceivedResult == null)
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "onNotificationProcessing throw an exception. Displaying normal OneSignal notification.", t);
         else
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "onNotificationProcessing throw an exception. Extended notification displayed but custom processing did not finish.", t);
      }

      // If the developer did not call displayNotification from onNotificationProcessing
      if (notificationReceivedResult == null) {
         // Save as processed to prevent possible duplicate calls from canonical ids.

         boolean display = !developerProcessed &&
               NotificationBundleProcessor.shouldDisplay(currentJsonPayload.optString("alert"));

         if (!display) {
            if (!restoring) {
               OSNotificationGenerationJob notifJob = new OSNotificationGenerationJob(context);
               notifJob.jsonPayload = currentJsonPayload;
               notifJob.overrideSettings = new OverrideSettings();
               notifJob.overrideSettings.androidNotificationId = -1;

               NotificationBundleProcessor.processNotification(notifJob, true);
               OneSignal.handleNotificationReceived(notifJob, false);
            }
            // If are are not displaying a restored notification make sure we mark it as dismissed
            //   This will prevent it from being restored again.
            else if (currentBaseOverrideSettings != null)
               NotificationBundleProcessor.markRestoredNotificationAsDismissed(createNotifJobFromCurrent(context));
         }
         else
            NotificationBundleProcessor.ProcessJobForDisplay(createNotifJobFromCurrent(context));

         // Delay to prevent CPU spikes.
         //    Normally more than one notification is restored at a time.
         if (restoring)
            OSUtils.sleep(100);
      }
   }

//   static Intent getIntent(Context context) {
//      PackageManager packageManager = context.getPackageManager();
//      Intent intent = new Intent().setAction("com.onesignal.NotificationExtender").setPackage(context.getPackageName());
//      List<ResolveInfo> resolveInfo = packageManager.queryIntentServices(intent, PackageManager.GET_META_DATA);
//      if (resolveInfo.size() < 1)
//         return null;
//
//      intent.setComponent(new ComponentName(context, resolveInfo.get(0).serviceInfo.name));
//
//      return intent;
//   }

   private OSNotificationGenerationJob createNotifJobFromCurrent(Context context) {
      OSNotificationGenerationJob notifJob = new OSNotificationGenerationJob(context);
      notifJob.isRestoring = currentlyRestoring;
      notifJob.jsonPayload = currentJsonPayload;
      notifJob.shownTimeStamp = restoreTimestamp;
      notifJob.overrideSettings = currentBaseOverrideSettings;

      return notifJob;
   }

   /**
    * In addition to using the setters to set all of the handlers you can also create your own implementation
    *  within a separate class and give your AndroidManifest.xml a special meta data tag
    * The meta data tag looks like this:
    *  <meta-data android:name="com.onesignal.NotificationExtensionServiceClass" android:value="com.company.ExtensionService" />
    * <br/><br/>
    * TODO: Comment about {@link OneSignal.NotificationProcessingHandler}
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
    * @see OneSignal#setNotificationProcessingHandler
    * @see OneSignal.ExtNotificationWillShowInForegroundHandler
    * @see OneSignal#setExtNotificationWillShowInForegroundHandler
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

         // Attempt to find an implementation for the NotificationProcessingHandler and then set it using package-private setter
         if (clazzInstance instanceof OneSignal.NotificationProcessingHandler) {
            OneSignal.setNotificationProcessingHandler((OneSignal.NotificationProcessingHandler) clazzInstance);
         }

         // Attempt to find an implementation for the ExtNotificationWillShowInForegroundHandler and then set it using package-private setter
         if (clazzInstance instanceof OneSignal.ExtNotificationWillShowInForegroundHandler) {
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
}