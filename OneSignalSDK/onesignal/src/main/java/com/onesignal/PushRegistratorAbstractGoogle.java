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

import android.content.Context;

import java.io.IOException;

// Both PushRegistratorFCM and PushRegistratorGCM extend this class
// Only getToken() needs to be implement for FCM and GCM
// Both throw the same exceptions so this does the error handling and retry logic
abstract class PushRegistratorAbstractGoogle implements PushRegistrator {
   private RegisteredHandler registeredHandler;

   private static int REGISTRATION_RETRY_COUNT = 5;
   private static int REGISTRATION_RETRY_BACKOFF_MS = 10_000;

   abstract String getProviderName();
   abstract String getToken(String senderId) throws Throwable;

   @Override
   public void registerForPush(Context context, String senderId, RegisteredHandler callback) {
      registeredHandler = callback;

      if (isValidProjectNumber(senderId, callback))
         internalRegisterForPush(senderId);
   }

   private void internalRegisterForPush(String senderId) {
      try {
         if (GooglePlayServicesUpgradePrompt.isGMSInstalledAndEnabled())
            registerInBackground(senderId);
         else {
            GooglePlayServicesUpgradePrompt.ShowUpdateGPSDialog();
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "'Google Play services' app not installed or disabled on the device.");
            registeredHandler.complete(null, UserState.PUSH_STATUS_OUTDATED_GOOGLE_PLAY_SERVICES_APP);
         }
      } catch (Throwable t) {
         OneSignal.Log(
            OneSignal.LOG_LEVEL.ERROR,
            "Could not register with "
              + getProviderName() +
              " due to an issue with your AndroidManifest.xml or with 'Google Play services'.",
            t
         );
         registeredHandler.complete(null, UserState.PUSH_STATUS_FIREBASE_FCM_INIT_ERROR);
      }
   }

   private Thread registerThread;
   private synchronized void registerInBackground(final String senderId) {
      // If any thread is still running, don't create a new one
      if (registerThread != null && registerThread.isAlive())
         return;

      registerThread = new Thread(new Runnable() {
         public void run() {
            for (int currentRetry = 0; currentRetry < REGISTRATION_RETRY_COUNT; currentRetry++) {
               boolean finished = attemptRegistration(senderId, currentRetry);
               if (finished)
                  return;
               OSUtils.sleep(REGISTRATION_RETRY_BACKOFF_MS * (currentRetry + 1));
            }
         }
      });
      registerThread.start();
   }

   private boolean firedCallback;
   private boolean attemptRegistration(String senderId, int currentRetry) {
      try {
         String registrationId = getToken(senderId);
         OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "Device registered, push token = " + registrationId);
         registeredHandler.complete(registrationId, UserState.PUSH_STATUS_SUBSCRIBED);
         return true;
      } catch (IOException e) {
         if (!"SERVICE_NOT_AVAILABLE".equals(e.getMessage())) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error Getting " + getProviderName() + " Token", e);
            if (!firedCallback)
               registeredHandler.complete(null, UserState.PUSH_STATUS_FIREBASE_FCM_ERROR_IOEXCEPTION);
            return true;
         }
         else {
            if (currentRetry >= (REGISTRATION_RETRY_COUNT - 1))
               OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Retry count of " + REGISTRATION_RETRY_COUNT + " exceed! Could not get a " + getProviderName() + " Token.", e);
            else {
               OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "'Google Play services' returned SERVICE_NOT_AVAILABLE error. Current retry count: " + currentRetry, e);
               if (currentRetry == 2) {
                  // Retry 3 times before firing a null response and continuing a few more times.
                  registeredHandler.complete(null, UserState.PUSH_STATUS_FIREBASE_FCM_ERROR_SERVICE_NOT_AVAILABLE);
                  firedCallback = true;
                  return true;
               }
            }
         }
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Unknown error getting " + getProviderName() + " Token", t);
         registeredHandler.complete(null, UserState.PUSH_STATUS_FIREBASE_FCM_ERROR_MISC_EXCEPTION);
         return true;
      }

      return false;
   }

   private boolean isValidProjectNumber(String senderId, PushRegistrator.RegisteredHandler callback) {
      boolean isProjectNumberValidFormat;
      try {
         Float.parseFloat(senderId);
         isProjectNumberValidFormat = true;
      } catch(Throwable t) {
         isProjectNumberValidFormat = false;
      }

      if (!isProjectNumberValidFormat) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Missing Google Project number!\nPlease enter a Google Project number / Sender ID on under App Settings > Android > Configuration on the OneSignal dashboard.");
         callback.complete(null, UserState.PUSH_STATUS_INVALID_FCM_SENDER_ID);
         return false;
      }
      return true;
   }
}
