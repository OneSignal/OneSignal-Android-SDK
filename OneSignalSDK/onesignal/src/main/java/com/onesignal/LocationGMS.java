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

import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.onesignal.AndroidSupportV4Compat.ContextCompat;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

class LocationGMS {

   static class LocationPoint {
      Double lat;
      Double log;
      Float accuracy;
      Integer type;
      Boolean bg;
      Long timeStamp;
   }

   private static final long TIME_FOREGROUND_SEC = 5 * 60;
   private static final long TIME_BACKGROUND_SEC = 10 * 60;
   private static final long FOREGROUND_UPDATE_TIME_MS = (TIME_FOREGROUND_SEC - 30) * 1_000;
   private static final long BACKGROUND_UPDATE_TIME_MS = (TIME_BACKGROUND_SEC - 30) * 1_000;

   private static GoogleApiClientCompatProxy mGoogleApiClient;
   private static Location mLastLocation;
   static String requestPermission;
   private static Context classContext;

   private static LocationHandlerThread locationHandlerThread;

   protected static final Object syncLock = new Object() {};

   enum PermissionType {
      STARTUP, PROMPT_LOCATION, SYNC_SERVICE
   }
   interface LocationHandler {
      PermissionType getType();
      void complete(LocationPoint point);
   }

   abstract static class LocationPromptCompletionHandler implements LocationHandler {
      void onAnswered(boolean accepted) {}
   }

   private static ConcurrentHashMap<PermissionType, LocationHandler> locationHandlers = new ConcurrentHashMap<>();
   private static final List<LocationPromptCompletionHandler> promptHandlers = new ArrayList<>();

   private static Thread fallbackFailThread;

   private static boolean locationCoarse;

   static boolean scheduleUpdate(Context context) {
      if (!hasLocationPermission(context) || !OneSignal.shareLocation)
         return false;

      long lastTime = System.currentTimeMillis() - getLastLocationTime();
      long minTime = 1_000 * (OneSignal.isForeground() ? TIME_FOREGROUND_SEC : TIME_BACKGROUND_SEC);
      long scheduleTime = minTime - lastTime;

      OneSignalSyncServiceUtils.scheduleLocationUpdateTask(context, scheduleTime);
      return true;
   }

   private static void setLastLocationTime(long time) {
      OneSignalPrefs.saveLong(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_OS_LAST_LOCATION_TIME,time);
   }

   private static long getLastLocationTime() {
      return OneSignalPrefs.getLong(
               OneSignalPrefs.PREFS_ONESIGNAL,
               OneSignalPrefs.PREFS_OS_LAST_LOCATION_TIME,
               TIME_BACKGROUND_SEC * -1_000
      );
   }

   private static boolean hasLocationPermission(Context context) {
      return ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED
          || ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_COARSE_LOCATION") == PackageManager.PERMISSION_GRANTED;
   }

   static void addPromptHandlerIfAvailable(LocationHandler handler) {
      if (handler instanceof LocationPromptCompletionHandler) {
         synchronized (promptHandlers) {
            promptHandlers.add((LocationPromptCompletionHandler) handler);
         }
      }
   }

   static void sendAndClearPromptHandlers(boolean promptLocation, boolean accepted) {
      if (!promptLocation) {
         OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "LocationGMS sendAndClearPromptHandlers from non prompt flow");
         return;
      }

      synchronized (promptHandlers) {
         OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "LocationGMS calling prompt handlers");
         for (LocationPromptCompletionHandler promptHandler : promptHandlers) {
            promptHandler.onAnswered(accepted);
         }
         // We only call the prompt handlers once
         promptHandlers.clear();
      }
   }

   /**
    * This method handle location and permission location flows and border cases.
    * For each flow we need to trigger location prompts listener,
    * in that way all listener will now that location request completed, even if its showing a prompt
    *
    * Cases managed:
    *    - If app doesn't have location sharing activated, then location will not attributed
    *    - For API less than 23, prompt permission aren't needed
    *    - For API greater or equal than 23
    *        - Ask for permission if needed, this will prompt PermissionActivity
    *        - If permission granted, then trigger location attribution
    *        - If permission denied, then trigger fail flow
    *    - If location service is disable, then trigger fail flow
    *         - If the user approved for location and has disable location this will continue triggering fails flows
    *
    *  For all cases we are calling prompt listeners.
    */
   static void getLocation(Context context, boolean promptLocation, LocationHandler handler) {
      addPromptHandlerIfAvailable(handler);
      classContext = context;
      locationHandlers.put(handler.getType(), handler);

      if (!OneSignal.shareLocation) {
         sendAndClearPromptHandlers(promptLocation, false);
         fireFailedComplete();
         return;
      }

      int locationCoarsePermission = PackageManager.PERMISSION_DENIED;

      int locationFinePermission = ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_FINE_LOCATION");
      if (locationFinePermission == PackageManager.PERMISSION_DENIED) {
         locationCoarsePermission = ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_COARSE_LOCATION");
         locationCoarse = true;
      }

      if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
         if (locationFinePermission != PackageManager.PERMISSION_GRANTED && locationCoarsePermission != PackageManager.PERMISSION_GRANTED) {
            sendAndClearPromptHandlers(promptLocation,false);
            handler.complete(null);
            return;
         }
         sendAndClearPromptHandlers(promptLocation, true);
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
               } else {
                  OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "Location permissions not added on AndroidManifest file");
               }

               // We handle the following cases:
               //  1 - If needed and available then prompt for permissions
               //       - Request permission can be ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION
               //  2 - If the permission were already granted then start getting location
               //  3 - If permission wasn't granted then trigger fail flow
               //
               // For each case, we call the prompt handlers
               if (requestPermission != null && promptLocation) {
                  PermissionsActivity.startPrompt();
               } else if (locationCoarsePermission == PackageManager.PERMISSION_GRANTED) {
                  sendAndClearPromptHandlers(promptLocation, true);
                  startGetLocation();
               } else {
                  sendAndClearPromptHandlers(promptLocation, false);
                  fireFailedComplete();
               }
            } catch (PackageManager.NameNotFoundException e) {
                  sendAndClearPromptHandlers(promptLocation, false);
               e.printStackTrace();
            }
         }
         else {
            sendAndClearPromptHandlers(promptLocation, true);
            startGetLocation();
         }
      }
   }

   // Started from this class or PermissionActivity
   static void startGetLocation() {
      // Prevents overlapping requests
      if (fallbackFailThread != null)
         return;

      try {
         synchronized (syncLock) {
            startFallBackThread();

            if (locationHandlerThread == null)
               locationHandlerThread = new LocationHandlerThread();

            if (mGoogleApiClient == null || mLastLocation == null) {
               GoogleApiClientListener googleApiClientListener = new GoogleApiClientListener();
               GoogleApiClient googleApiClient = new GoogleApiClient.Builder(classContext)
                       .addApi(LocationServices.API)
                       .addConnectionCallbacks(googleApiClientListener)
                       .addOnConnectionFailedListener(googleApiClientListener)
                       .setHandler(locationHandlerThread.mHandler)
                       .build();
               mGoogleApiClient = new GoogleApiClientCompatProxy(googleApiClient);

               mGoogleApiClient.connect();
            }
            else if (mLastLocation != null)
               fireCompleteForLocation(mLastLocation);
         }
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "Location permission exists but there was an error initializing: ", t);
         fireFailedComplete();
      }
   }

   private static int getApiFallbackWait() {
      return 30_000;
   }

   private static void startFallBackThread() {
      fallbackFailThread = new Thread(new Runnable() {
         public void run() {
            try {
               Thread.sleep(getApiFallbackWait());
               OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "Location permission exists but GoogleApiClient timed out. Maybe related to mismatch google-play aar versions.");
               fireFailedComplete();
               scheduleUpdate(classContext);
            } catch (InterruptedException e) {
               // Interruptions expected when connection is made to the api
            }
         }
      }, "OS_GMS_LOCATION_FALLBACK");
      fallbackFailThread.start();
   }

   static void fireFailedComplete() {
      PermissionsActivity.answered = false;

      synchronized (syncLock) {
         if (mGoogleApiClient != null)
            mGoogleApiClient.disconnect();
         mGoogleApiClient = null;
      }
      fireComplete(null);
   }

   private static void fireComplete(LocationPoint point) {
      // create local copies of fields in thread-safe way
      HashMap<PermissionType, LocationHandler> _locationHandlers = new HashMap<>();
      Thread _fallbackFailThread;
      synchronized (LocationGMS.class) {
         _locationHandlers.putAll(LocationGMS.locationHandlers);
         LocationGMS.locationHandlers.clear();
         _fallbackFailThread = LocationGMS.fallbackFailThread;
      }

      // execute race-independent logic
      for(PermissionType type : _locationHandlers.keySet())
         _locationHandlers.get(type).complete(point);
      if (_fallbackFailThread != null && !Thread.currentThread().equals(_fallbackFailThread))
          _fallbackFailThread.interrupt();

      // clear fallbackFailThread in thread-safe way
      if (_fallbackFailThread == LocationGMS.fallbackFailThread) {
         synchronized (LocationGMS.class) {
            if (_fallbackFailThread == LocationGMS.fallbackFailThread)
               LocationGMS.fallbackFailThread = null;
         }
      }
      // Save last time so even if a failure we trigger the same schedule update
      setLastLocationTime(System.currentTimeMillis());
   }

   private static void fireCompleteForLocation(Location location) {
      LocationPoint point = new LocationPoint();

      point.accuracy = location.getAccuracy();
      point.bg = !OneSignal.isForeground();
      point.type = locationCoarse ? 0 : 1;
      point.timeStamp = location.getTime();

      // Coarse always gives out 14 digits and has an accuracy 2000.
      // Always rounding to 7 as this is what fine returns.
      if (locationCoarse) {
         point.lat = new BigDecimal(location.getLatitude()).setScale(7, RoundingMode.HALF_UP).doubleValue();
         point.log = new BigDecimal(location.getLongitude()).setScale(7, RoundingMode.HALF_UP).doubleValue();
      }
      else {
         point.lat = location.getLatitude();
         point.log = location.getLongitude();
      }

      fireComplete(point);
      scheduleUpdate(classContext);
   }

   static void onFocusChange() {
      synchronized (syncLock) {
         if (mGoogleApiClient == null || !mGoogleApiClient.realInstance().isConnected())
            return;

         GoogleApiClient googleApiClient = mGoogleApiClient.realInstance();

         if (locationUpdateListener != null)
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, locationUpdateListener);

         locationUpdateListener = new LocationUpdateListener(googleApiClient);
      }
   }

   static LocationUpdateListener locationUpdateListener;

   private static class GoogleApiClientListener implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
      @Override
      public void onConnected(Bundle bundle) {
         synchronized (syncLock) {
            PermissionsActivity.answered = false;

            if (mGoogleApiClient == null || mGoogleApiClient.realInstance() == null)
               return;

            if (mLastLocation == null) {
               mLastLocation = FusedLocationApiWrapper.getLastLocation(mGoogleApiClient.realInstance());
               if (mLastLocation != null)
                  fireCompleteForLocation(mLastLocation);
            }

            locationUpdateListener = new LocationUpdateListener(mGoogleApiClient.realInstance());
         }
      }

      @Override
      public void onConnectionSuspended(int i) {
         fireFailedComplete();
      }

      @Override
      public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
         fireFailedComplete();
      }
   }

   static class LocationUpdateListener implements LocationListener {

      private GoogleApiClient mGoogleApiClient;

      // this initializer method is already synchronized from LocationGMS with respect to the GoogleApiClient lock
      LocationUpdateListener(GoogleApiClient googleApiClient) {
         mGoogleApiClient = googleApiClient;

         long updateInterval = BACKGROUND_UPDATE_TIME_MS;
         if (OneSignal.isForeground())
            updateInterval =  FOREGROUND_UPDATE_TIME_MS;

         LocationRequest locationRequest = LocationRequest.create()
            .setFastestInterval(updateInterval)
            .setInterval(updateInterval)
            .setMaxWaitTime((long)(updateInterval * 1.5))
            .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

         FusedLocationApiWrapper.requestLocationUpdates(mGoogleApiClient, locationRequest, this);
      }

      @Override
      public void onLocationChanged(Location location) {
         mLastLocation = location;
         OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "Location Change Detected");
      }
   }

   static class FusedLocationApiWrapper {
      @SuppressWarnings("MissingPermission")
      static void requestLocationUpdates(GoogleApiClient googleApiClient, LocationRequest locationRequest, LocationListener locationListener) {
         try {
            synchronized (LocationGMS.syncLock) {
               if (googleApiClient.isConnected())
                  LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, locationListener);
            }
         } catch(Throwable t) {
            OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "FusedLocationApi.requestLocationUpdates failed!", t);
         }
      }

      @SuppressWarnings("MissingPermission")
      static Location getLastLocation(GoogleApiClient googleApiClient) {
         synchronized(LocationGMS.syncLock) {
            if (googleApiClient.isConnected())
               return LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
         }
         return null;
      }
   }

   private static class LocationHandlerThread extends HandlerThread {
      Handler mHandler;

      LocationHandlerThread() {
         super("OSH_LocationHandlerThread");
         start();
         mHandler = new Handler(getLooper());
      }
   }
}