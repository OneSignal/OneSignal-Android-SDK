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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.telephony.TelephonyManager;

class OSUtils {
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
}