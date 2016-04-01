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

import com.onesignal.AndroidSupportV4Compat.ContextCompat;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

class LocationGMS {
   private static GoogleApiClientCompatProxy mGoogleApiClient;
   static String requestPermission;

   interface LocationHandler {
      void complete(Double lat, Double log, Float accuracy, Integer type);
   }

   private static LocationHandler locationHandler;

   private static Thread fallbackFailThread;

   private static boolean locationCoarse;

   static void getLocation(Context context, boolean promptLocation, LocationHandler handler) {
      locationHandler = handler;
      int locationCoarsePermission = PackageManager.PERMISSION_DENIED;

      int locationFinePermission = ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_FINE_LOCATION");
      if (locationFinePermission == PackageManager.PERMISSION_DENIED) {
         locationCoarsePermission = ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_COARSE_LOCATION");
         locationCoarse = true;
      }

      if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
         if (locationFinePermission != PackageManager.PERMISSION_GRANTED && locationCoarsePermission != PackageManager.PERMISSION_GRANTED) {
            handler.complete(null, null, null, null);
            return;
         }

         startGetLocation();
      }
      else { // Android 6.0+
         if (locationFinePermission != PackageManager.PERMISSION_GRANTED) {
            try {
               PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
               List<String> permissionList = Arrays.asList(packageInfo.requestedPermissions);
               if (permissionList.contains("android.permission.ACCESS_FINE_LOCATION"))
                  requestPermission = "android.permission.ACCESS_FINE_LOCATION";
               else if (permissionList.contains("android.permission.ACCESS_COARSE_LOCATION")) {
                  if (locationCoarsePermission != PackageManager.PERMISSION_GRANTED)
                     requestPermission = "android.permission.ACCESS_COARSE_LOCATION";
               }

               if (requestPermission != null && promptLocation)
                  PermissionsActivity.startPrompt();
               else if (locationCoarsePermission == PackageManager.PERMISSION_GRANTED)
                  startGetLocation();
               else
                  fireFailedComplete();
            } catch (Throwable t) {
               t.printStackTrace();
            }
         }
         else
            startGetLocation();
      }
   }

   static void startGetLocation() {
      try {
         startFallBackThread();

         GoogleApiClientListener googleApiClientListener = new GoogleApiClientListener();
         GoogleApiClient googleApiClient = new GoogleApiClient.Builder(OneSignal.appContext)
             .addApi(LocationServices.API)
             .addConnectionCallbacks(googleApiClientListener)
             .addOnConnectionFailedListener(googleApiClientListener)
             .build();
         mGoogleApiClient = new GoogleApiClientCompatProxy(googleApiClient);

         mGoogleApiClient.connect();
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "Location permission exists but there was an error initializing: ", t);
         fireFailedComplete();
      }
   }

   private static void startFallBackThread() {
      fallbackFailThread = new Thread(new Runnable() {
         public void run() {
            try {
               Thread.sleep(30000);
               OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "Location permission exists but GoogleApiClient timedout. Maybe related to mismatch google-play aar versions.");
               fireFailedComplete();
            } catch (Throwable t) {}
         }
      });
      fallbackFailThread.start();
   }

   static void fireFailedComplete() {
      PermissionsActivity.answered = false;

      fireComplete(null, null, null, null);
      if (mGoogleApiClient != null)
         mGoogleApiClient.disconnect();
   }

   private static void fireComplete(Double lat, Double log, Float accuracy, Integer type) {
      locationHandler.complete(lat, log, accuracy, type);
      if (fallbackFailThread != null && !Thread.currentThread().equals(fallbackFailThread))
         fallbackFailThread.interrupt();
   }

   private static class GoogleApiClientListener implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
      @Override
      public void onConnected(Bundle bundle) {
         PermissionsActivity.answered = false;

         Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient.realInstance());

         // Coarse always gives out 14 digits and has an accuracy 2000. Always rounding to 7 as this is what fine returns.
         if (location != null) {
            location.getAccuracy();
            Double lat = new BigDecimal(location.getLatitude()).setScale(7, RoundingMode.HALF_UP).doubleValue();
            Double log = new BigDecimal(location.getLongitude()).setScale(7, RoundingMode.HALF_UP).doubleValue();
            fireComplete(lat, log, location.getAccuracy(), locationCoarse ? 0 : 1);
         }
         else
            fireComplete(null, null, null, null);

         mGoogleApiClient.disconnect();
      }

      @Override
      public void onConnectionSuspended(int i) {
         fireFailedComplete();
      }

      @Override
      public void onConnectionFailed(ConnectionResult connectionResult) {
         fireFailedComplete();
      }
   }
}
