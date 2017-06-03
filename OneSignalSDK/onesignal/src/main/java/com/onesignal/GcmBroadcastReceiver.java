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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.WakefulBroadcastReceiver;
import com.onesignal.NotificationBundleProcessor.ProcessedBundleResult;

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
      // Google Play services started sending an extra non-ordered broadcast with the bundle:
      //    { "COM": "RST_FULL", "from": "google.com/iid" }
      // Result codes are not valid with non-ordered broadcasts so omit it to prevent errors to the logcat.
      Bundle bundle = intent.getExtras();
      if (bundle == null || "google.com/iid".equals(bundle.getString("from")))
         return;
   
      ProcessedBundleResult processedResult = processOrderBroadcast(context, intent, bundle);
      
      // Null means this isn't a GCM / FCM message.
      if (processedResult == null) {
         setResultCode(Activity.RESULT_OK);
         return;
      }
      
      // Prevent other GCM receivers from firing if;
      //   This is a duplicated GCM message
      //   OR app developer setup a extender service to handle the notification.
      if (processedResult.isDup || processedResult.hasExtenderService) {
         // Abort to prevent other GCM receivers from process this Intent.
         abortBroadcast();
         return;
      }
   
      // Prevent other GCM receivers from firing if;
      //   This is a OneSignal payload
      //   AND the setting is enabled to allow filtering in this case.
      if (processedResult.isOneSignalPayload &&
          OneSignal.getFilterOtherGCMReceivers(context)) {
         
         abortBroadcast();
         return;
      }

      setResultCode(Activity.RESULT_OK);
   }
   
   private static ProcessedBundleResult processOrderBroadcast(Context context, Intent intent, Bundle bundle) {
      if (!isGcmMessage(intent))
         return null;
      
      ProcessedBundleResult processedResult = NotificationBundleProcessor.processBundle(context, bundle);
   
      // Return if the notification will NOT be handled by normal GcmIntentService display flow.
      if (processedResult.processed())
         return processedResult;

      Intent intentForService = new Intent();
      intentForService.putExtra("json_payload", NotificationBundleProcessor.bundleAsJSONObject(bundle).toString());
      intentForService.putExtra("timestamp", System.currentTimeMillis() / 1000L);
      intentForService.setComponent(new ComponentName(context.getPackageName(),
                                                      GcmIntentService.class.getName()));
      startWakefulService(context, intentForService);
      
      return processedResult;
   }
}
