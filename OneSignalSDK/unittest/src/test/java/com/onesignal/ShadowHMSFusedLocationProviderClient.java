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

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import com.huawei.hmf.tasks.Task;
import com.huawei.hmf.tasks.a.i;
import com.huawei.hms.location.FusedLocationProviderClient;
import com.huawei.hms.location.LocationCallback;
import com.huawei.hms.location.LocationRequest;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(FusedLocationProviderClient.class)
public class ShadowHMSFusedLocationProviderClient {

    Context context;
    public static Double lat, log;
    public static Float accuracy;
    public static Integer type;
    public static Long time;
    public static boolean shadowTask = false;
    public static boolean skipOnGetLocation = false;

    public static void resetStatics() {
        lat = 1.0;
        log = 2.0;
        accuracy = 3.0f;
        type = 0;
        time = 12345L;
        shadowTask = false;
        skipOnGetLocation = false;
    }

    @Implementation
    public void __constructor__(Context context) {
        this.context = context;
    }

    public static Location getLocation() {
        Location location = new Location("");
        location.setLatitude(lat);
        location.setLongitude(log);
        location.setAccuracy(accuracy);
        location.setTime(time);
        return location;
    }

    @Implementation
    public i<Location> getLastLocation() {
        final i<Location> locationTask = new i<>();
        if (shadowTask || skipOnGetLocation)
            return locationTask;

        timerLocationComplete(locationTask);
        return locationTask;

    }

    @Implementation
    public i<Void> requestLocationUpdates(LocationRequest var1, LocationCallback var2, Looper var3) {
        return new i<>();
    }

    @Implementation
    public Task<Void> removeLocationUpdates(LocationCallback var1) {
        return new i<>();
    }

    private static void timerLocationComplete(final i<Location> locationTask) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                locationTask.a(getLocation());
            }
        }, 1000);
    }

}
