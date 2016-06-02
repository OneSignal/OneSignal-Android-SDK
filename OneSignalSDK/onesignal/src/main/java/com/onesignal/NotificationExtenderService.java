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

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

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

// NOTE: Currently does not support Amazon ADM messages.

public abstract class NotificationExtenderService extends IntentService {

   public class OverrideSettings {
      public NotificationCompat.Extender extender;

      // Possible future options
      //    int androidNotificationId;
      //     - Need to consider DB records when replacing.
      //    int badgeCount;
      //   NotificationCompat.Extender summaryExtender;
   }

   public NotificationExtenderService() {
      super("NotificationExtenderService");
   }

   private OSNotificationDisplayedResult osNotificationDisplayedResult;
   private Bundle currentExtras;

   // Developer may call to override some notification settings.
   //   - If called the normal SDK notification will not be displayed.
   protected final OSNotificationDisplayedResult displayNotification(OverrideSettings overrideSettings) {
      if (osNotificationDisplayedResult != null || overrideSettings == null)
         return null;

      osNotificationDisplayedResult = new OSNotificationDisplayedResult();
      osNotificationDisplayedResult.notificationId = NotificationBundleProcessor.Process(this, currentExtras, overrideSettings);
      return osNotificationDisplayedResult;
   }

   // App developer must implement
   //   - Return true to count it as processed to prevent the default OneSignal notification from displaying.
   protected abstract boolean onNotificationProcessing(OSNotificationPayload notification);

   @Override
   protected final void onHandleIntent(Intent intent) {
      processIntent(intent);
      GcmBroadcastReceiver.completeWakefulIntent(intent);
   }

   private void processIntent(Intent intent) {
      currentExtras = intent.getExtras();

      if (OneSignal.notValidOrDuplicated(this, currentExtras))
         return;

      OSNotificationPayload notification = new OSNotificationPayload();
      try {
         JSONObject customJson = new JSONObject(currentExtras.getString("custom"));
         notification.notificationId = customJson.optString("i");
         notification.additionalData = customJson.optJSONObject("a");
         notification.launchUrl = customJson.optString("u", null);

         notification.message = currentExtras.getString("alert");
         notification.title = currentExtras.getString("title");
         notification.smallIcon = currentExtras.getString("sicon");
         notification.bigPicture = currentExtras.getString("bicon");
         notification.largeIcon = currentExtras.getString("licon");
         notification.sound = currentExtras.getString("sound");
         notification.group = currentExtras.getString("grp");
         notification.groupMessage = currentExtras.getString("grp_msg");
         notification.backgroundColor = currentExtras.getString("bgac");
         notification.ledColor = currentExtras.getString("ledc");
         String visibility = currentExtras.getString("vis");
         if (visibility != null)
            notification.visibility = Integer.parseInt(visibility);
         notification.backgroundData = "1".equals(currentExtras.getString("bgn"));
         notification.fromProjectNumber = currentExtras.getString("from");

         if (notification.additionalData != null && notification.additionalData.has("actionButtons")) {
            JSONArray jsonActionButtons = notification.additionalData.getJSONArray("actionButtons");
            notification.actionButtons = new ArrayList<>();

            for (int i = 0; i < jsonActionButtons.length(); i++) {
               JSONObject jsonActionButton = jsonActionButtons.getJSONObject(i);
               OSNotificationPayload.ActionButton actionButton = new OSNotificationPayload.ActionButton();
               actionButton.id = jsonActionButton.getString("id");
               actionButton.text = jsonActionButton.getString("text");
               actionButton.icon = jsonActionButton.optString("icon", null);
               notification.actionButtons.add(actionButton);
            }
            notification.additionalData.remove("actionSelected");
            notification.additionalData.remove("actionButtons");
         }
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error assigning OSNotificationPayload values!", t);
      }

      osNotificationDisplayedResult = null;
      boolean developerProcessed = false;
      try {
         developerProcessed = onNotificationProcessing(notification);
      }
      catch (Throwable t) {
         //noinspection ConstantConditions - displayNotification might have been called by the developer
         if (osNotificationDisplayedResult == null)
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "onNotificationProcessing throw an exception. Displaying normal OneSignal notification. ", t);
         else
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "onNotificationProcessing throw an exception. Extended notification displayed but custom processing did not finish.", t);
      }

      // If developer did not call displayNotification from onNotificationProcessing
      if (osNotificationDisplayedResult == null) {
         // Save as processed to prevent possible duplicate calls from canonical ids.
         if (developerProcessed)
            NotificationBundleProcessor.saveNotification(this, currentExtras, true, -1);
         else
            NotificationBundleProcessor.Process(this, currentExtras, null);
      }
   }

   static Intent getIntent(Context context, Bundle extras) {
      PackageManager packageManager = context.getPackageManager();
      Intent intent = new Intent().setAction("com.onesignal.NotificationExtender").setPackage(context.getPackageName());
      List<ResolveInfo> resolveInfo = packageManager.queryIntentServices(intent, PackageManager.GET_META_DATA);
      if (resolveInfo.size() < 1)
         return null;

      intent.putExtras(extras);
      return intent;
   }
}