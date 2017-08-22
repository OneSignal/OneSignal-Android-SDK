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

import com.onesignal.NotificationBundleProcessor.ProcessedBundleResult;

import java.util.Random;

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

   @Override
   public void onReceive(Context context, Intent intent) {
      // Do not process token update messages here.
      // They are also non-ordered broadcasts.
      Bundle bundle = intent.getExtras();
      if (bundle == null || "google.com/iid".equals(bundle.getString("from")))
         return;
      
      ProcessedBundleResult processedResult = processOrderBroadcast(context, intent, bundle);
      
      // Null means this isn't a GCM / FCM message.
      if (processedResult == null) {
         setResult(Activity.RESULT_OK);
         return;
      }
      
      // Prevent other GCM receivers from firing if;
      //   This is a duplicated GCM message
      //   OR app developer setup a extender service to handle the notification.
      if (processedResult.isDup || processedResult.hasExtenderService) {
         // Abort to prevent other GCM receivers from process this Intent.
         setAbort();
         return;
      }
   
      // Prevent other GCM receivers from firing if;
      //   This is a OneSignal payload
      //   AND the setting is enabled to allow filtering in this case.
      if (processedResult.isOneSignalPayload &&
          OneSignal.getFilterOtherGCMReceivers(context)) {
         setAbort();
         return;
      }

      setResult(Activity.RESULT_OK);
   }

   private void setResult(int code) {
      if (isOrderedBroadcast())
         setResultCode(code);
   }

   private void setAbort() {
      if (isOrderedBroadcast())
         abortBroadcast();
   }
   
   private static ProcessedBundleResult processOrderBroadcast(Context context, Intent intent, Bundle bundle) {
      if (!isGcmMessage(intent))
         return null;
      
      ProcessedBundleResult processedResult = NotificationBundleProcessor.processBundleFromReceiver(context, bundle);
   
      // Return if the notification will NOT be handled by normal GcmIntentService display flow.
      if (processedResult.processed())
         return processedResult;
   
      startGCMService(context, bundle);
      
      return processedResult;
   }
   
   private static void startGCMService(Context context, Bundle bundle) {
      BundleCompat taskExtras;
      
      // If no remote resources have to be downloaded don't create a job which could add some delay.
      if (!NotificationBundleProcessor.hasRemoteResource(bundle)) {
         taskExtras = setCompatBundleForServer(bundle, BundleCompatFactory.getInstance());
         NotificationBundleProcessor.ProcessFromGCMIntentService(context, taskExtras, null);
         return;
      }
      
      boolean isHighPriority = Integer.parseInt(bundle.getString("pri", "0")) > 9;
      if (!isHighPriority && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
         taskExtras = setCompatBundleForServer(bundle, BundleCompatFactory.getInstance());
         
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
      else {
         ComponentName componentName = new ComponentName(context.getPackageName(),
                                                         GcmIntentService.class.getName());
         
         taskExtras = setCompatBundleForServer(bundle, new BundleCompatBundle());
         Intent intentForService = new Intent()
                                    .replaceExtras((Bundle)taskExtras.getBundle())
                                    .setComponent(componentName);
         startWakefulService(context, intentForService);
      }
   }
   
   private static BundleCompat setCompatBundleForServer(Bundle bundle, BundleCompat taskExtras) {
      taskExtras.putString("json_payload", NotificationBundleProcessor.bundleAsJSONObject(bundle).toString());
      taskExtras.putLong("timestamp", System.currentTimeMillis() / 1000L);
      return taskExtras;
   }
}
