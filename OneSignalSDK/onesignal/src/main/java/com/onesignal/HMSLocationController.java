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

import com.huawei.hms.location.FusedLocationProviderClient;
import com.huawei.hms.location.LocationCallback;
import com.huawei.hms.location.LocationRequest;
import com.huawei.hms.location.LocationResult;

class HMSLocationController extends LocationController {

    private static FusedLocationProviderClient hmsFusedLocationClient;
    static LocationUpdateListener locationUpdateListener;

    // Started from this class or PermissionActivity
    static void startGetLocation() {
        initHuaweiLocation();
    }

    private static void initHuaweiLocation() {
        synchronized (syncLock) {
            if (hmsFusedLocationClient == null) {
                try {
                    hmsFusedLocationClient = com.huawei.hms.location.LocationServices.getFusedLocationProviderClient(classContext);
                } catch (Exception e) {
                    OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Huawei LocationServices getFusedLocationProviderClient failed! " + e);
                    fireFailedComplete();
                    return;
                }
            }
            if (lastLocation != null)
                fireCompleteForLocation(lastLocation);
            else
                hmsFusedLocationClient.getLastLocation()
                        .addOnSuccessListener(new com.huawei.hmf.tasks.OnSuccessListener<Location>() {
                            @Override
                            public void onSuccess(Location location) {
                                OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "Huawei LocationServices getLastLocation returned location: " + location);
                                if (location == null) {
                                    fireFailedComplete();
                                    return;
                                }
                                lastLocation = location;
                                fireCompleteForLocation(lastLocation);
                                locationUpdateListener = new LocationUpdateListener(hmsFusedLocationClient);
                            }
                        })
                        .addOnFailureListener(new com.huawei.hmf.tasks.OnFailureListener() {
                            @Override
                            public void onFailure(Exception e) {
                                OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Huawei LocationServices getLastLocation failed!", e);
                                fireFailedComplete();
                            }
                        });
        }
    }

    static void fireFailedComplete() {
        synchronized (syncLock) {
            hmsFusedLocationClient = null;
        }
    }

    static void onFocusChange() {
        synchronized (syncLock) {
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "HMSLocationController onFocusChange!");

            // Huawei location not initialized yet
            if (isHMSAvailable() && hmsFusedLocationClient == null)
                return;

            if (hmsFusedLocationClient != null) {
                if (locationUpdateListener != null)
                    hmsFusedLocationClient.removeLocationUpdates(locationUpdateListener);
                locationUpdateListener = new LocationUpdateListener(hmsFusedLocationClient);
            }
        }
    }

    static class LocationUpdateListener extends LocationCallback {

        private FusedLocationProviderClient huaweiFusedLocationProviderClient;

        LocationUpdateListener(FusedLocationProviderClient huaweiFusedLocationProviderClient) {
            this.huaweiFusedLocationProviderClient = huaweiFusedLocationProviderClient;
            init();
        }

        private void init() {
            long updateInterval = BACKGROUND_UPDATE_TIME_MS;
            if (OneSignal.isInForeground())
                updateInterval = FOREGROUND_UPDATE_TIME_MS;

            LocationRequest locationRequest = LocationRequest.create()
                    .setFastestInterval(updateInterval)
                    .setInterval(updateInterval)
                    .setMaxWaitTime((long) (updateInterval * 1.5))
                    .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "HMSLocationController Huawei LocationServices requestLocationUpdates!");
            huaweiFusedLocationProviderClient.requestLocationUpdates(locationRequest, this, getLocationHandlerThread().getLooper());
        }

        @Override
        public void onLocationResult(LocationResult locationResult) {
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "HMSLocationController onLocationResult: " + locationResult);
            if (locationResult != null)
                lastLocation = locationResult.getLastLocation();
        }
    }

}