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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.NonNull;

import com.onesignal.AndroidSupportV4Compat.ContextCompat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

class LocationController {

   private static final long TIME_FOREGROUND_SEC = 5 * 60;
   private static final long TIME_BACKGROUND_SEC = 10 * 60;
   static final long FOREGROUND_UPDATE_TIME_MS = (TIME_FOREGROUND_SEC - 30) * 1_000;
   static final long BACKGROUND_UPDATE_TIME_MS = (TIME_BACKGROUND_SEC - 30) * 1_000;

   private static final List<LocationPromptCompletionHandler> promptHandlers = new ArrayList<>();
   private static ConcurrentHashMap<PermissionType, LocationHandler> locationHandlers = new ConcurrentHashMap<>();
   private static boolean locationCoarse;

   static final Object syncLock = new Object() {};

   private static LocationHandlerThread locationHandlerThread;
   static LocationHandlerThread getLocationHandlerThread() {
      if (locationHandlerThread == null) {
         synchronized (syncLock) {
            if (locationHandlerThread == null)
               locationHandlerThread = new LocationHandlerThread();
         }
      }
      return locationHandlerThread;
   }

   static Thread fallbackFailThread;
   static Context classContext;
   static Location lastLocation;

   static String requestPermission;

   enum PermissionType {
      STARTUP, PROMPT_LOCATION, SYNC_SERVICE;
   }

   static class LocationPoint {
      Double lat;
      Double log;
      Float accuracy;
      Integer type;
      Boolean bg;
      Long timeStamp;

      @Override
      public String toString() {
         return "LocationPoint{" +
                 "lat=" + lat +
                 ", log=" + log +
                 ", accuracy=" + accuracy +
                 ", type=" + type +
                 ", bg=" + bg +
                 ", timeStamp=" + timeStamp +
                 '}';
      }
   }

   interface LocationHandler {
      PermissionType getType();
      void onComplete(LocationPoint point);
   }

   abstract static class LocationPromptCompletionHandler implements LocationHandler {
      void onAnswered(OneSignal.PromptActionResult result) {}
   }

   static boolean scheduleUpdate(Context context) {
      if (!hasLocationPermission(context) || !OneSignal.isLocationShared())
         return false;

      long lastTime = System.currentTimeMillis() - getLastLocationTime();
      long minTime = 1_000 * (OneSignal.isInForeground() ? TIME_FOREGROUND_SEC : TIME_BACKGROUND_SEC);
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

   private static void addPromptHandlerIfAvailable(LocationHandler handler) {
      if (handler instanceof LocationPromptCompletionHandler) {
         synchronized (promptHandlers) {
            promptHandlers.add((LocationPromptCompletionHandler) handler);
         }
      }
   }

   static void sendAndClearPromptHandlers(boolean promptLocation, OneSignal.PromptActionResult result) {
      if (!promptLocation) {
         OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "LocationController sendAndClearPromptHandlers from non prompt flow");
         return;
      }

      synchronized (promptHandlers) {
         OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "LocationController calling prompt handlers");
         for (LocationPromptCompletionHandler promptHandler : promptHandlers) {
            promptHandler.onAnswered(result);
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
   static void getLocation(Context context, boolean promptLocation, boolean fallbackToSettings, LocationHandler handler) {
      addPromptHandlerIfAvailable(handler);
      classContext = context;
      locationHandlers.put(handler.getType(), handler);

      if (!OneSignal.isLocationShared()) {
         sendAndClearPromptHandlers(promptLocation, OneSignal.PromptActionResult.ERROR);
         fireFailedComplete();
         return;
      }

      int locationCoarsePermission = PackageManager.PERMISSION_DENIED;

      int locationFinePermission = ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_FINE_LOCATION");
      if (locationFinePermission == PackageManager.PERMISSION_DENIED) {
         locationCoarsePermission = ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_COARSE_LOCATION");
         locationCoarse = true;
      }

      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
         if (locationFinePermission != PackageManager.PERMISSION_GRANTED && locationCoarsePermission != PackageManager.PERMISSION_GRANTED) {
            // Permission missing on manifest
            sendAndClearPromptHandlers(promptLocation, OneSignal.PromptActionResult.LOCATION_PERMISSIONS_MISSING_MANIFEST);

            handler.onComplete(null);
            return;
         }
         sendAndClearPromptHandlers(promptLocation, OneSignal.PromptActionResult.PERMISSION_GRANTED);
         startGetLocation();
      }
      else { // Android 6.0+
         if (locationFinePermission != PackageManager.PERMISSION_GRANTED) {
            try {
               PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
               List<String> permissionList = Arrays.asList(packageInfo.requestedPermissions);
               OneSignal.PromptActionResult result = OneSignal.PromptActionResult.PERMISSION_DENIED;
               if (permissionList.contains("android.permission.ACCESS_FINE_LOCATION"))
                  // ACCESS_FINE_LOCATION permission defined on Manifest, prompt for permission
                  // If permission already given prompt will return positive, otherwise will prompt again or show settings
                  requestPermission = "android.permission.ACCESS_FINE_LOCATION";
               else if (permissionList.contains("android.permission.ACCESS_COARSE_LOCATION")) {
                  if (locationCoarsePermission != PackageManager.PERMISSION_GRANTED) {
                     // ACCESS_COARSE_LOCATION permission defined on Manifest, prompt for permission
                     // If permission already given prompt will return positive, otherwise will prompt again or show settings
                     requestPermission = "android.permission.ACCESS_COARSE_LOCATION";
                  }
               } else {
                  OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "Location permissions not added on AndroidManifest file");
                  result = OneSignal.PromptActionResult.LOCATION_PERMISSIONS_MISSING_MANIFEST;
               }

               // We handle the following cases:
               //  1 - If needed and available then prompt for permissions
               //       - Request permission can be ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION
               //  2 - If the permission were already granted then start getting location
               //  3 - If permission wasn't granted then trigger fail flow
               //
               // For each case, we call the prompt handlers
               if (requestPermission != null && promptLocation) {
                  PermissionsActivity.startPrompt(fallbackToSettings);
               } else if (locationCoarsePermission == PackageManager.PERMISSION_GRANTED) {
                  sendAndClearPromptHandlers(promptLocation, OneSignal.PromptActionResult.PERMISSION_GRANTED);
                  startGetLocation();
               } else {
                  sendAndClearPromptHandlers(promptLocation, result);
                  fireFailedComplete();
               }
            } catch (PackageManager.NameNotFoundException e) {
               sendAndClearPromptHandlers(promptLocation, OneSignal.PromptActionResult.ERROR);
               e.printStackTrace();
            }
         }
         else {
            sendAndClearPromptHandlers(promptLocation, OneSignal.PromptActionResult.PERMISSION_GRANTED);
            startGetLocation();
         }
      }
   }

   // Started from this class or PermissionActivity
   static void startGetLocation() {
      OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "LocationController startGetLocation with lastLocation: " + lastLocation);

      try {
         if (isGooglePlayServicesAvailable()) {
            GMSLocationController.startGetLocation();
         } else if (isHMSAvailable()) {
            HMSLocationController.startGetLocation();
         } else {
            fireFailedComplete();
         }
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "Location permission exists but there was an error initializing: ", t);
         fireFailedComplete();
      }
   }

   static void onFocusChange() {
      synchronized (syncLock) {
         if (isGooglePlayServicesAvailable()) {
            GMSLocationController.onFocusChange();
            return;
         }

         if (isHMSAvailable())
            HMSLocationController.onFocusChange();
      }
   }

   // If we are using device type Android for push we can safely assume we are using Google Play services
   static boolean isGooglePlayServicesAvailable() {
      return OSUtils.isAndroidDeviceType() && OSUtils.hasGMSLocationLibrary();
   }

   // If we are using device type Huawei for push we can safely assume we are using HMS Core
   static boolean isHMSAvailable() {
      return OSUtils.isHuaweiDeviceType() && OSUtils.hasHMSLocationLibrary();
   }

   static void fireFailedComplete() {
      PermissionsActivity.answered = false;

      synchronized (syncLock) {
         if (isGooglePlayServicesAvailable())
            GMSLocationController.fireFailedComplete();
         else if (isHMSAvailable())
            HMSLocationController.fireFailedComplete();
      }
      fireComplete(null);
   }

   private static void fireComplete(LocationPoint point) {
      // create local copies of fields in thread-safe way
      HashMap<PermissionType, LocationHandler> _locationHandlers = new HashMap<>();
      Thread _fallbackFailThread;
      synchronized (LocationController.class) {
         _locationHandlers.putAll(LocationController.locationHandlers);
         LocationController.locationHandlers.clear();
         _fallbackFailThread = LocationController.fallbackFailThread;
      }

      // execute race-independent logic
      for(PermissionType type : _locationHandlers.keySet())
         _locationHandlers.get(type).onComplete(point);
      if (_fallbackFailThread != null && !Thread.currentThread().equals(_fallbackFailThread))
         _fallbackFailThread.interrupt();

      // clear fallbackFailThread in thread-safe way
      if (_fallbackFailThread == LocationController.fallbackFailThread) {
         synchronized (LocationController.class) {
            if (_fallbackFailThread == LocationController.fallbackFailThread)
               LocationController.fallbackFailThread = null;
         }
      }
      // Save last time so even if a failure we trigger the same schedule update
      setLastLocationTime(System.currentTimeMillis());
   }

   protected static void fireCompleteForLocation(Location location) {
      OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "LocationController fireCompleteForLocation with location: " + location);
      LocationPoint point = new LocationPoint();

      point.accuracy = location.getAccuracy();
      point.bg = !OneSignal.isInForeground();
      point.type = locationCoarse ? 0 : 1;
      point.timeStamp = location.getTime();

      // Coarse always gives out 14 digits and has an accuracy 2000.
      // Always rounding to 7 as this is what fine returns.
      if (locationCoarse) {
         point.lat = new BigDecimal(location.getLatitude()).setScale(7, RoundingMode.HALF_UP).doubleValue();
         point.log = new BigDecimal(location.getLongitude()).setScale(7, RoundingMode.HALF_UP).doubleValue();
      } else {
         point.lat = location.getLatitude();
         point.log = location.getLongitude();
      }

      fireComplete(point);
      scheduleUpdate(classContext);
   }

   protected static class LocationHandlerThread extends HandlerThread {
      Handler mHandler;

      LocationHandlerThread() {
         super("OSH_LocationHandlerThread");
         start();
         mHandler = new Handler(getLooper());
      }
   }
}