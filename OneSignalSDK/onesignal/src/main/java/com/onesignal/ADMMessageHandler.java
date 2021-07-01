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

import androidx.annotation.Nullable;

import com.amazon.device.messaging.ADMMessageHandlerJobBase;
import com.amazon.device.messaging.ADMMessageHandlerBase;
import com.amazon.device.messaging.ADMMessageReceiver;

import org.json.JSONObject;

// WARNING: Do not pass 'this' to any methods as it will cause proguard build errors
//             when "proguard-android-optimize.txt" is used.
public class ADMMessageHandler extends ADMMessageHandlerBase {

   private static final int JOB_ID = 123891;

   public static class Receiver extends ADMMessageReceiver {
      public Receiver() {
         super(ADMMessageHandler.class);
         boolean ADMLatestAvailable = false;
         try {
            Class.forName( "com.amazon.device.messaging.ADMMessageHandlerJobBase" );
            ADMLatestAvailable = true ;
         }
         catch (ClassNotFoundException e)
         {
            // Handle the exception.
         }
         if (ADMLatestAvailable) {
            registerJobServiceClass(ADMMessageHandlerJob.class, JOB_ID);
         }
         OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "ADM latest available: " + ADMLatestAvailable);
      }
   }

   public ADMMessageHandler() {
      super("ADMMessageHandler");
   }

   @Override
   protected void onMessage(Intent intent) {
      final Context context = getApplicationContext();
      final Bundle bundle = intent.getExtras();

      NotificationBundleProcessor.ProcessBundleReceiverCallback bundleReceiverCallback = new NotificationBundleProcessor.ProcessBundleReceiverCallback() {
         @Override
         public void onBundleProcessed(@Nullable NotificationBundleProcessor.ProcessedBundleResult processedResult) {
            // TODO: Figure out the correct replacement or usage of completeWakefulIntent method
            //      FCMBroadcastReceiver.completeWakefulIntent(intent);

            if (processedResult.processed())
               return;

            JSONObject payload = NotificationBundleProcessor.bundleAsJSONObject(bundle);
            OSNotification notification = new OSNotification(payload);

            OSNotificationGenerationJob notificationJob = new OSNotificationGenerationJob(context);
            notificationJob.setJsonPayload(payload);
            notificationJob.setContext(context);
            notificationJob.setNotification(notification);
            NotificationBundleProcessor.processJobForDisplay(notificationJob, true);
         }
      };
      NotificationBundleProcessor.processBundleFromReceiver(context, bundle, bundleReceiverCallback);
   }

   @Override
   protected void onRegistered(String newRegistrationId) {
      OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "ADM registration ID: " + newRegistrationId);
      PushRegistratorADM.fireCallback(newRegistrationId);
   }

   @Override
   protected void onRegistrationError(String error) {
      OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "ADM:onRegistrationError: " + error);
      if ("INVALID_SENDER".equals(error))
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Please double check that you have a matching package name (NOTE: Case Sensitive), api_key.txt, and the apk was signed with the same Keystore and Alias.");
      
      PushRegistratorADM.fireCallback(null);
   }

   @Override
   protected void onUnregistered(String info) {
      OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "ADM:onUnregistered: " + info);
   }
}