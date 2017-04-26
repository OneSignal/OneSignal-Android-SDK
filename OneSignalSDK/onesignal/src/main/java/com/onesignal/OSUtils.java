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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.NotificationManagerCompat;
import android.telephony.TelephonyManager;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

class OSUtils {

   static final int UNINITIALIZABLE_STATUS = -999;

   int initializationChecker(int deviceType, String oneSignalAppId) {
      int subscribableStatus = 1;

      try {
         //noinspection ResultOfMethodCallIgnored
         UUID.fromString(oneSignalAppId);
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.FATAL, "OneSignal AppId format is invalid.\nExample: 'b2f7f966-d8cc-11e4-bed1-df8f05be55ba'\n", t);
         return UNINITIALIZABLE_STATUS;
      }

      if ("b2f7f966-d8cc-11e4-bed1-df8f05be55ba".equals(oneSignalAppId) ||
          "5eb5a37e-b458-11e3-ac11-000c2940e62c".equals(oneSignalAppId))
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "OneSignal Example AppID detected, please update to your app's id found on OneSignal.com");

      if (deviceType == 1) {
         try {
            Class.forName("com.google.android.gms.gcm.GoogleCloudMessaging");
         } catch (ClassNotFoundException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.FATAL, "The GCM Google Play services client library was not found. Please make sure to include it in your project.", e);
            subscribableStatus = -4;
         }

         try {
            Class.forName("com.google.android.gms.common.GooglePlayServicesUtil");
         } catch (ClassNotFoundException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.FATAL, "The GooglePlayServicesUtil class part of Google Play services client library was not found. Include this in your project.", e);
            subscribableStatus = -4;
         }
      }

      try {
         Class.forName("android.support.v4.view.MenuCompat");
         try {
            Class.forName("android.support.v4.content.WakefulBroadcastReceiver");
            Class.forName("android.support.v4.app.NotificationManagerCompat");
         } catch (ClassNotFoundException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.FATAL, "The included Android Support Library v4 is to old or incomplete. Please update your project's android-support-v4.jar to the latest revision.", e);
            subscribableStatus = -5;
         }
      } catch (ClassNotFoundException e) {
         OneSignal.Log(OneSignal.LOG_LEVEL.FATAL, "Could not find the Android Support Library v4. Please make sure android-support-v4.jar has been correctly added to your project.", e);
         subscribableStatus = -3;
      }

      return subscribableStatus;
   }

   int getDeviceType() {
      try {
         // Class only available on the FireOS and only when the following is in the AndroidManifest.xml.
         // <amazon:enable-feature android:name="com.amazon.device.messaging" android:required="false"/>
         Class.forName("com.amazon.device.messaging.ADM");
         return 2;
      } catch (ClassNotFoundException e) {
         return 1;
      }
   }

   Integer getNetType () {
      ConnectivityManager cm = (ConnectivityManager) OneSignal.appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkInfo netInfo = cm.getActiveNetworkInfo();

      if (netInfo != null) {
         int networkType = netInfo.getType();
         if (networkType == ConnectivityManager.TYPE_WIFI || networkType == ConnectivityManager.TYPE_ETHERNET)
            return 0;
         return 1;
      }

      return null;
   }

   String getCarrierName() {
      TelephonyManager manager = (TelephonyManager)OneSignal.appContext.getSystemService(Context.TELEPHONY_SERVICE);
      String carrierName = manager.getNetworkOperatorName();
      return "".equals(carrierName) ? null : carrierName;
   }

   static String getManifestMeta(Context context, String metaName) {
      try {
         ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
         Bundle bundle = ai.metaData;
         return bundle.getString(metaName);
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "", t);
      }

      return null;
   }

   static String getResourceString(Context context, String key, String defaultStr) {
      Resources resources = context.getResources();
      int bodyResId = resources.getIdentifier(key, "string", context.getPackageName());
      if (bodyResId != 0)
         return resources.getString(bodyResId);
      return defaultStr;
   }

   static String getCorrectedLanguage() {
      String lang = Locale.getDefault().getLanguage();

      // https://github.com/OneSignal/OneSignal-Android-SDK/issues/64
      if (lang.equals("iw"))
         return "he";
      if (lang.equals("in"))
         return "id";
      if (lang.equals("ji"))
         return "yi";

      // https://github.com/OneSignal/OneSignal-Android-SDK/issues/98
      if (lang.equals("zh"))
         return lang + "-" + Locale.getDefault().getCountry();

      return lang;
   }

   static boolean isValidEmail(String email) {
      if (email == null)
         return false;

      String emRegex = "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\])|(([a-zA-Z\\-0-9]+\\.)+[a-zA-Z]{2,}))$";
      Pattern pattern = Pattern.compile(emRegex);
      return pattern.matcher(email).matches();
   }
   
   // Get the app's permission which will be false if the user disabled notifications for the app
   //   from Settings > Apps or by long pressing the notifications and selecting block.
   //   - Detection works on Android 4.4+, requires Android Support v4 Library 24.0.0+
   static boolean areNotificationsEnabled(Context  context) {
      try {
         return NotificationManagerCompat.from(OneSignal.appContext).areNotificationsEnabled();
      } catch (Throwable t) {}
      
      return true;
   }
   
   static void runOnMainUIThread(Runnable runnable) {
      if (Looper.getMainLooper().getThread() == Thread.currentThread())
         runnable.run();
      else {
         Handler handler = new Handler(Looper.getMainLooper());
         handler.post(runnable);
      }
   }
}