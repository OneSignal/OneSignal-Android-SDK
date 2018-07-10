/**
 * Modified MIT License
 * 
 * Copyright 2018 OneSignal
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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import com.onesignal.NotificationBundleProcessor.ProcessedBundleResult;

import java.util.Random;

// This is the entry point when a FCM / GCM payload is received from
//   the Google Play services app (com.google.android.gms)
// The broadcast will be ordered on pre-Oreo devices and unordered on Android Oreo+
public class GcmBroadcastReceiver extends WakefulBroadcastReceiver {

   private static final String GCM_RECEIVE_ACTION = "com.google.android.c2dm.intent.RECEIVE";
   private static final String GCM_TYPE = "gcm";
   private static final String MESSAGE_TYPE_EXTRA_KEY = "message_type";

   private static boolean isGcmMessage(Intent intent) {
      if (GCM_RECEIVE_ACTION.equals(intent.getAction())) {
         String messageType = intent.getStringExtra(MESSAGE_TYPE_EXTRA_KEY);
         return (messageType == null || GCM_TYPE.equals(messageType));
      }
      return false;
   }

   // Ordered VS Un-Ordered broadcasts here.
   // These intents are unordered on GMS version 11580448 on Android 8.1
   // On same device version 12.8.74 of GMS is back to ordered broadcasts....
   // Using FCM or GCM libraries had no effect

   @Override
   public void onReceive(Context context, Intent intent) {
      Log.w("OneSignal", "isOrderedBroadcast(): " + isOrderedBroadcast());
      boolean processed = processIntent(context, intent);
      handleBroadcastResult(context, intent, processed);
   }

   private void handleBroadcastResult(Context context, Intent intent, boolean processed) {
      Log.w("OneSignal", "handleBroadcastResult: " + processed);
      if (processed) {
         if (OneSignal.getFilterOtherGCMReceivers()) {
            if (isOrderedBroadcast())
               abortBroadcast();
            // else, broadcast receivers already disabled before this event even fired
         }
         else if (isOrderedBroadcast())
            setResultCode(Activity.RESULT_OK);
      }
      else {
         if (isOrderedBroadcast())
            setResultCode(Activity.RESULT_OK);
         else if (OneSignal.getFilterOtherGCMReceivers()) {
            Log.w("OneSignal", "Calling FCMIntentFilterHelper.sendBroadcastToRuntimeReceivers");
            FCMIntentFilterHelper.sendBroadcastToRuntimeReceivers(context, intent);
         }
      }
   }

   private static boolean isTokenUpdate(Bundle bundle) {
      return "google.com/iid".equals(bundle.getString("from"));
   }

   private static boolean processIntent(Context context, Intent intent) {
      Bundle bundle = intent.getExtras();

      // Do not process token update messages here
      if (bundle == null || isTokenUpdate(bundle))
         return false;

      if (!isGcmMessage(intent))
         return false;

      ProcessedBundleResult processedResult = processBundle(context, bundle);
      return processedResult.isOneSignalPayload;
   }
   
   private static ProcessedBundleResult processBundle(Context context, Bundle bundle) {
      ProcessedBundleResult processedResult
         = NotificationBundleProcessor.processBundleFromReceiver(context, bundle);

      if (!processedResult.processed())
         startGCMService(context, bundle);
      
      return processedResult;
   }
   
   private static void startGCMService(Context context, Bundle bundle) {
      // If no remote resources have to be downloaded don't create a job which could add some delay.
      if (!NotificationBundleProcessor.hasRemoteResource(bundle)) {
         BundleCompat taskExtras = setCompatBundleForServer(bundle, BundleCompatFactory.getInstance());
         NotificationBundleProcessor.ProcessFromGCMIntentService(context, taskExtras, null);
         return;
      }
      
      boolean isHighPriority = Integer.parseInt(bundle.getString("pri", "0")) > 9;
      if (!isHighPriority && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
         startGCMServiceWithJobScheduler(context, bundle);
      else {
         try {
            startGCMServiceWithWakefulService(context, bundle);
         } catch (IllegalStateException e) {
            // If the high priority FCM message failed to add this app to the temporary whitelist
            // https://github.com/OneSignal/OneSignal-Android-SDK/issues/498
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
               startGCMServiceWithWakefulService(context, bundle);
            else
               throw e;
         }
      }
   }

   @TargetApi(Build.VERSION_CODES.LOLLIPOP)
   private static void startGCMServiceWithJobScheduler(Context context, Bundle bundle) {
      BundleCompat taskExtras = setCompatBundleForServer(bundle, BundleCompatFactory.getInstance());

      ComponentName componentName = new ComponentName(context.getPackageName(),
         GcmIntentJobService.class.getName());
      Random random = new Random();
      JobInfo jobInfo = new JobInfo.Builder(random.nextInt(), componentName)
         .setOverrideDeadline(0)
         .setExtras((PersistableBundle)taskExtras.getBundle())
         .build();
      JobScheduler jobScheduler = (JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

      // TODO: Might want to use enqueue in the future. This will process one notification
      //         sequentially like an IntentService
      //       JobIntentService can be used instead, however app developer would have to use
      //         Android support library 26+
      jobScheduler.schedule(jobInfo);
   }

   private static void startGCMServiceWithWakefulService(Context context, Bundle bundle) {
      ComponentName componentName =
         new ComponentName(context.getPackageName(), GcmIntentService.class.getName());

      BundleCompat taskExtras = setCompatBundleForServer(bundle, new BundleCompatBundle());
      Intent intentForService =
         new Intent()
         .replaceExtras((Bundle)taskExtras.getBundle())
         .setComponent(componentName);
      startWakefulService(context, intentForService);
   }

   private static BundleCompat setCompatBundleForServer(Bundle bundle, BundleCompat taskExtras) {
      taskExtras.putString("json_payload", NotificationBundleProcessor.bundleAsJSONObject(bundle).toString());
      taskExtras.putLong("timestamp", System.currentTimeMillis() / 1000L);
      return taskExtras;
   }
}
