package com.onesignal;

import com.onesignal.AndroidSupportV4Compat.ContextCompat;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

class LocationGMS {
   private static GoogleApiClient mGoogleApiClient;
   static String requestPermission;

   private static ActivityLifecycleHandler.ActivityAvailableListener activityAvailableListener;

   interface LocationHandler {
      void complete(Double lat, Double log);
   }

   private static LocationHandler locationHandler;

   static void getLocation(Context context, boolean promptLocation, LocationHandler handler) {
      locationHandler = handler;
      int locationCoarsePermission = PackageManager.PERMISSION_DENIED;

      int locationFinePermission = ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_FINE_LOCATION");
      if (locationFinePermission == PackageManager.PERMISSION_DENIED)
         locationCoarsePermission = ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_COARSE_LOCATION");

      if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
         if (locationFinePermission != PackageManager.PERMISSION_GRANTED && locationCoarsePermission != PackageManager.PERMISSION_GRANTED) {
            handler.complete(null, null);
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
      GoogleApiClientListener googleApiClientListener = new GoogleApiClientListener();
      mGoogleApiClient = new GoogleApiClient.Builder(OneSignal.appContext)
          .addApi(LocationServices.API)
          .addConnectionCallbacks(googleApiClientListener)
          .addOnConnectionFailedListener(googleApiClientListener)
          .build();
      mGoogleApiClient.connect();
   }

   static void fireFailedComplete() {
      PermissionsActivity.answered = false;

      locationHandler.complete(null, null);
      if (mGoogleApiClient != null)
         mGoogleApiClient.disconnect();
   }

   private static class GoogleApiClientListener implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
      @Override
      public void onConnected(Bundle bundle) {
         PermissionsActivity.answered = false;

         Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
         // Coarse always gives out 14 digits and has an accuracy 2000. Always rounding to 7 as this is what fine returns.
         if (location != null)
            locationHandler.complete(new BigDecimal(location.getLatitude()).setScale(7, RoundingMode.HALF_UP).doubleValue(), new BigDecimal(location.getLongitude()).setScale(7, RoundingMode.HALF_UP).doubleValue());
         else
            locationHandler.complete(null, null);

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
