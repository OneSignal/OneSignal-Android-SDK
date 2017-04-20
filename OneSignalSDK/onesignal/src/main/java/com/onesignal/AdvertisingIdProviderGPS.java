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

import com.google.android.gms.ads.identifier.AdvertisingIdClient;

import android.content.Context;

class AdvertisingIdProviderGPS implements AdvertisingIdentifierProvider {
   
   private static String lastValue;
   
   static String getLastValue() {
      return lastValue;
   }

   @Override
   public String getIdentifier(Context appContext) {
      try {
         AdvertisingIdClient.Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(appContext);
         if (adInfo.isLimitAdTrackingEnabled())
            lastValue = "OptedOut"; // Google restricts usage of the id to "build profiles" if the user checks opt out so we can't collect.
         else
            lastValue = adInfo.getId();
         
         return lastValue;
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "Error getting Google Ad id: ", t);
      }

      // IOException                             = Unrecoverable error connecting to Google Play services (e.g., the old version of the service doesn't support getting AdvertisingId).
      // GooglePlayServicesNotAvailableException = Google Play services is not available entirely.
      // IllegalStateException                   = Unknown error
      // GooglePlayServicesRepairableException   = Google Play Services is not installed, up-to-date, or enabled

      return null;
   }
}
