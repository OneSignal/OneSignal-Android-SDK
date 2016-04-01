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

import java.util.Arrays;
import java.util.List;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;

class AdvertisingIdProviderFallback implements AdvertisingIdentifierProvider {

   private static final List<String> INVALID_PHONE_IDS = Arrays.asList("", "0", "unknown", "739463", "000000000000000", "111111111111111", "352005048247251", "012345678912345", "012345678901237",
         "88508850885050", "0123456789abcde", "004999010640000", "862280010599525", "52443443484950", "355195000000017", "001068000000006", "358673013795895", "355692547693084", "004400152020000",
         "8552502717594321", "113456798945455", "012379000772883", "111111111111119", "358701042909755", "358000043654134", "345630000000115", "356299046587760", "356591000000222");

   @Override
   public String getIdentifier(Context appContext) {
      String id;

      id = getPhoneId(appContext);
      if (id != null)
         return id;

      id = getAndroidId(appContext);
      if (id != null)
         return id;

      return getWifiMac(appContext);
   }

   // Requires android.permission.READ_PHONE_STATE permission
   private String getPhoneId(Context appContext) {
      try {
         final String phoneId = ((TelephonyManager) appContext.getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId();
         if (phoneId != null && !INVALID_PHONE_IDS.contains(phoneId))
            return phoneId;
      } catch (RuntimeException e) {}
      return null;
   }

   private String getAndroidId(Context appContext) {
      try {
         final String androidId = Settings.Secure.getString(appContext.getContentResolver(), Settings.Secure.ANDROID_ID);
         // see http://code.google.com/p/android/issues/detail?id=10603 for info on this 'dup' id.
         if (androidId != "9774d56d682e549c")
            return androidId;
      } catch (RuntimeException e) {}

      return null;
   }

   // Requires android.permission.ACCESS_WIFI_STATE permission
   private String getWifiMac(Context appContext) {
      try {
         if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return ((WifiManager) appContext.getSystemService(Context.WIFI_SERVICE)).getConnectionInfo().getMacAddress();
      } catch (RuntimeException e) {}

      return null;
   }
}