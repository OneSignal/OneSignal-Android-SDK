package com.onesignal;

import android.location.Location;

import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

/**
 * Copyright 2017 OneSignal
 * Created by alamgir on 9/22/17.
 */

@Implements(LocationGMS.LocationUpdateListener.class)
public class ShadowLocationUpdateListener {

//    @RealObject
//    LocationGMS.LocationUpdateListener realListener;

    public static void provideFakeLocation(Location location) {
        LocationGMS.locationUpdateListener.onLocationChanged(location);
//        realListener.onLocationChanged(location);
    }

}
