package com.onesignal;

import android.location.Location;

import org.robolectric.annotation.Implements;

/**
 * Copyright 2017 OneSignal
 * Created by alamgir on 9/22/17.
 */

@Implements(LocationController.LocationUpdateListener.class)
public class ShadowLocationUpdateListener {

    public static void provideFakeLocation(Location location) {
        LocationController.locationUpdateListener.onLocationChanged(location);
    }

}
