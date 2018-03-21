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

import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent.CanceledException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.IOException;

import static com.onesignal.OSUtils.getResourceString;

public class PushRegistratorGPS implements PushRegistrator {

   private Context appContext;
   private RegisteredHandler registeredHandler;

   private static int GCM_RETRY_COUNT = 5;

   @Override
   public void registerForPush(Context context, String googleProjectNumber, RegisteredHandler callback) {
      appContext = context;
      registeredHandler = callback;
      
      boolean isProjectNumberValidFormat;
      try {
         Float.parseFloat(googleProjectNumber);
         isProjectNumberValidFormat = true;
      } catch(Throwable t) {
         isProjectNumberValidFormat = false;
      }
      
      if (!isProjectNumberValidFormat) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Missing Google Project number!\nPlease enter a Google Project number / Sender ID on under App Settings > Android > Configuration on the OneSignal dashboard.");
         registeredHandler.complete(null, -6);
         return;
      }
      
      internalRegisterForPush(googleProjectNumber);
   }
   
   private void internalRegisterForPush(String googleProjectNumber) {
      try {
         if (isGMSInstalledAndEnabled())
            registerInBackground(googleProjectNumber);
         else {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "'Google Play services' app not installed or disabled on the device.");
            registeredHandler.complete(null, -7);
         }
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Could not register with GCM due to an error with the AndroidManifest.xml file or with 'Google Play services'.", t);
         registeredHandler.complete(null, -8);
      }
   }

   private boolean isGooglePlayStoreInstalled() {
      try {
         PackageManager pm = appContext.getPackageManager();
         PackageInfo info = pm.getPackageInfo("com.android.vending", PackageManager.GET_ACTIVITIES);
         String label = (String) info.applicationInfo.loadLabel(pm);
         return (label != null && !label.equals("Market"));
      } catch (Throwable e) {}

      return false;
   }

   private boolean isGMSInstalledAndEnabled() {
      try {
         PackageManager pm = appContext.getPackageManager();
         PackageInfo info = pm.getPackageInfo("com.google.android.gms", PackageManager.GET_ACTIVITIES);

         if (!info.applicationInfo.enabled && isGooglePlayStoreInstalled()) {
            if(OneSignalPrefs.getBool(OneSignalPrefs.PREFS_ONESIGNAL,
                    OneSignalPrefs.PREFS_GT_DO_NOT_SHOW_MISSING_GPS,false))
               return false;

            try {
               ShowUpdateGPSDialog();
            } catch (Throwable t) {}
         }

         return info.applicationInfo.enabled;
      } catch (PackageManager.NameNotFoundException e) {}

      return false;
   }

   private void ShowUpdateGPSDialog() {
      OSUtils.runOnMainUIThread(new Runnable() {
         @Override
         public void run() {
            final Activity activity = ActivityLifecycleHandler.curActivity;
            if (activity == null || OneSignal.mInitBuilder.mDisableGmsMissingPrompt)
               return;

            String alertBodyText = getResourceString(activity, "onesignal_gms_missing_alert_text", "To receive push notifications please press 'Update' to enable 'Google Play services'.");
            String alertButtonUpdate = getResourceString(activity, "onesignal_gms_missing_alert_button_update", "Update");
            String alertButtonSkip = getResourceString(activity, "onesignal_gms_missing_alert_button_skip", "Skip");
            String alertButtonClose = getResourceString(activity, "onesignal_gms_missing_alert_button_close", "Close");

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setMessage(alertBodyText).setPositiveButton(alertButtonUpdate, new OnClickListener() {
               @Override
               public void onClick(DialogInterface dialog, int which) {
                  try {
                     int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(appContext);
                     GooglePlayServicesUtil.getErrorPendingIntent(resultCode, activity, 0).send();
                  } catch (CanceledException e) {
                  } catch (Throwable e) {
                     e.printStackTrace();
                  }

               }
            }).setNegativeButton(alertButtonSkip, new OnClickListener() {
               @Override
               public void onClick(DialogInterface dialog, int which) {
                  OneSignalPrefs.saveBool(OneSignalPrefs.PREFS_ONESIGNAL,
                          OneSignalPrefs.PREFS_GT_DO_NOT_SHOW_MISSING_GPS,true);

               }
            }).setNeutralButton(alertButtonClose, null).create().show();
         }
      });
   }

   private void registerInBackground(final String googleProjectNumber) {
      new Thread(new Runnable() {
         public void run() {
            boolean firedComplete = false;

            for (int currentRetry = 0; currentRetry < GCM_RETRY_COUNT; currentRetry++) {
               try {
                  GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(appContext);
                  String registrationId = gcm.register(googleProjectNumber);
                  OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "Device registered, Google Registration ID = " + registrationId);
                  registeredHandler.complete(registrationId, 1);
                  break;
               } catch (IOException e) {
                  if (!"SERVICE_NOT_AVAILABLE".equals(e.getMessage())) {
                     OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error Getting Google Registration ID", e);
                     if (!firedComplete)
                        registeredHandler.complete(null, -11);
                     break;
                  }
                  else {
                     if (currentRetry >= (GCM_RETRY_COUNT - 1))
                        OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "GCM_RETRY_COUNT of " + GCM_RETRY_COUNT + " exceed! Could not get a Google Registration Id", e);
                     else {
                        OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "Google Play services returned SERVICE_NOT_AVAILABLE error. Current retry count: " + currentRetry, e);
                        if (currentRetry == 2) {
                           // Retry 3 times before firing a null response and continuing a few more times.
                           registeredHandler.complete(null, -9);
                           firedComplete = true;
                        }
                        try { Thread.sleep(10000 * (currentRetry + 1)); } catch (Throwable t) {}
                     }
                  }
               } catch (Throwable t) {
                  OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error Getting Google Registration ID", t);
                  registeredHandler.complete(null, -12);
                  break;
               }
            }
         }
      }).start();
   }
}
