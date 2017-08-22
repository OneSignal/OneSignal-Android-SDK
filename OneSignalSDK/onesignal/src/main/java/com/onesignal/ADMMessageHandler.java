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

import com.amazon.device.messaging.ADMMessageHandlerBase;
import com.amazon.device.messaging.ADMMessageReceiver;

// WARNING: Do not pass 'this' to any methods as it will cause proguard build errors
//             when "proguard-android-optimize.txt" is used.
public class ADMMessageHandler extends ADMMessageHandlerBase {

   public static class Receiver extends ADMMessageReceiver {
      public Receiver() {
         super(ADMMessageHandler.class);
      }
   }

   public ADMMessageHandler() {
      super("ADMMessageHandler");
   }

   @Override
   protected void onMessage(Intent intent) {
      Context context = getApplicationContext();
      Bundle bundle = intent.getExtras();
      
      NotificationBundleProcessor.ProcessedBundleResult processedResult = NotificationBundleProcessor.processBundleFromReceiver(context, bundle);

      if (processedResult.processed())
         return;
      
      NotificationGenerationJob notifJob = new NotificationGenerationJob(context);
      notifJob.jsonPayload = NotificationBundleProcessor.bundleAsJSONObject(bundle);
      
      NotificationBundleProcessor.ProcessJobForDisplay(notifJob);
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