/**
 * Modified MIT License
 * <p>
 * Copyright 2020 OneSignal
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 * <p>
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
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

class GMSLocationController extends LocationController {

    static final int API_FALLBACK_TIME = 30_000;

    private static GoogleApiClientCompatProxy googleApiClient;
    static LocationUpdateListener locationUpdateListener;

    // Started from this class or PermissionActivity
    static void startGetLocation() {
        initGoogleLocation();
    }

    private static void initGoogleLocation() {
        // Prevents overlapping requests
        if (fallbackFailThread != null)
            return;

        synchronized (syncLock) {
            startFallBackThread();

            if (googleApiClient == null || lastLocation == null) {
                GoogleApiClientListener googleApiClientListener = new GoogleApiClientListener();
                GoogleApiClient googleApiClient = new GoogleApiClient.Builder(classContext)
                        .addApi(LocationServices.API)
                        .addConnectionCallbacks(googleApiClientListener)
                        .addOnConnectionFailedListener(googleApiClientListener)
                        .setHandler(getLocationHandlerThread().mHandler)
                        .build();

                GMSLocationController.googleApiClient = new GoogleApiClientCompatProxy(googleApiClient);
                GMSLocationController.googleApiClient.connect();
            } else if (lastLocation != null)
                fireCompleteForLocation(lastLocation);
        }
    }

    private static int getApiFallbackWait() {
        return API_FALLBACK_TIME;
    }

    private static void startFallBackThread() {
        fallbackFailThread = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(getApiFallbackWait());
                    OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "Location permission exists but GoogleApiClient timed out. Maybe related to mismatch google-play aar versions.");
                    LocationController.fireFailedComplete();
                    scheduleUpdate(classContext);
                } catch (InterruptedException e) {
                    // Interruptions expected when connection is made to the api
                }
            }
        }, "OS_GMS_LOCATION_FALLBACK");
        fallbackFailThread.start();
    }

    static void fireFailedComplete() {
        synchronized (syncLock) {
            if (googleApiClient != null)
                googleApiClient.disconnect();
            googleApiClient = null;
        }
    }

    static void onFocusChange() {
        synchronized (syncLock) {
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "GMSLocationController onFocusChange!");
            // Google location not initialized or connected yet
            if (googleApiClient == null || !googleApiClient.realInstance().isConnected())
                return;

            if (googleApiClient != null) {
                GoogleApiClient googleApiClient = GMSLocationController.googleApiClient.realInstance();
                if (locationUpdateListener != null)
                    LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, locationUpdateListener);
                locationUpdateListener = new LocationUpdateListener(googleApiClient);
            }
        }
    }

    private static class GoogleApiClientListener implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        @Override
        public void onConnected(Bundle bundle) {
            synchronized (syncLock) {
                PermissionsActivity.answered = false;

                if (googleApiClient == null || googleApiClient.realInstance() == null)
                    return;

                OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "LocationController GoogleApiClientListener onConnected lastLocation: " + lastLocation);
                if (lastLocation == null) {
                    lastLocation = FusedLocationApiWrapper.getLastLocation(googleApiClient.realInstance());
                    OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "LocationController GoogleApiClientListener lastLocation: " + lastLocation);
                    if (lastLocation != null)
                        fireCompleteForLocation(lastLocation);
                }

                locationUpdateListener = new LocationUpdateListener(googleApiClient.realInstance());
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

        private GoogleApiClient googleApiClient;

        // this initializer method is already synchronized from LocationController with respect to the GoogleApiClient lock
        LocationUpdateListener(GoogleApiClient googleApiClient) {
            this.googleApiClient = googleApiClient;
            init();
        }

        private void init() {
            long updateInterval = BACKGROUND_UPDATE_TIME_MS;
            if (OneSignal.isInForeground())
                updateInterval = FOREGROUND_UPDATE_TIME_MS;

            if (googleApiClient != null) {
                LocationRequest locationRequest = LocationRequest.create()
                        .setFastestInterval(updateInterval)
                        .setInterval(updateInterval)
                        .setMaxWaitTime((long) (updateInterval * 1.5))
                        .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

                OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "GMSLocationController GoogleApiClient requestLocationUpdates!");
                FusedLocationApiWrapper.requestLocationUpdates(googleApiClient, locationRequest, this);
            }
        }

        @Override
        public void onLocationChanged(Location location) {
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "GMSLocationController onLocationChanged: " + location);
            lastLocation = location;
        }
    }

    static class FusedLocationApiWrapper {
        @SuppressWarnings("MissingPermission")
        static void requestLocationUpdates(GoogleApiClient googleApiClient, LocationRequest locationRequest, LocationListener locationListener) {
            try {
                synchronized (GMSLocationController.syncLock) {
                    if (googleApiClient.isConnected())
                        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, locationListener);
                }
            } catch (Throwable t) {
                OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "FusedLocationApi.requestLocationUpdates failed!", t);
            }
        }

        @SuppressWarnings("MissingPermission")
        static Location getLastLocation(GoogleApiClient googleApiClient) {
            synchronized (GMSLocationController.syncLock) {
                if (googleApiClient.isConnected())
                    return LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
            }
            return null;
        }
    }
}