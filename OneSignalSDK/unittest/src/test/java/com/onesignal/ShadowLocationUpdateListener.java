package com.onesignal;

import android.location.Location;

import com.huawei.hms.location.HWLocation;
import com.huawei.hms.location.LocationResult;

import org.robolectric.annotation.Implements;

import java.util.ArrayList;
import java.util.List;

/**
 * Copyright 2017 OneSignal
 * Created by alamgir on 9/22/17.
 */

@Implements(LocationController.LocationUpdateListener.class)
public class ShadowLocationUpdateListener {

    public static void provideFakeLocation(Location location) {
        LocationController.locationUpdateListener.onLocationChanged(location);
    }

    public static void provideFakeLocation_Huawei(HWLocation location) {
        List<HWLocation> locations = new ArrayList<>();
        locations.add(location);
        LocationResult locationResult = LocationResult.create(locations);
        LocationController.locationUpdateListener.onLocationResult(locationResult);
    }
}
