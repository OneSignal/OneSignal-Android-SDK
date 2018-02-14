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

import android.location.Location;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;

import org.robolectric.annotation.Implements;

@Implements(LocationGMS.FusedLocationApiWrapper.class)
public class ShadowFusedLocationApiWrapper {
   
   public static Double lat, log;
   public static Float accuracy;
   public static Integer type;
   public static Long time;

   public static void resetStatics() {
      lat = 1.0;
      log = 2.0;
      accuracy = 3.0f;
      type = 0;
      time = 12345L;
   }
   
   public static void requestLocationUpdates(GoogleApiClient googleApiClient, LocationRequest locationRequest, LocationListener locationListener) {
   }
   
   public static void removeLocationUpdates(GoogleApiClient googleApiClient, LocationListener locationListener) {
   }
   
   public static Location getLastLocation(GoogleApiClient googleApiClient) {
      Location location = new Location("");
      location.setLatitude(lat); location.setLongitude(log);
      location.setAccuracy(accuracy);
      location.setTime(time);
      
      return location;
   }
}
