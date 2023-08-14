package com.onesignal;

import android.location.Location;

import org.robolectric.annotation.Implements;

import java.util.ArrayList;
import java.util.List;

@Implements(GMSLocationController.LocationUpdateListener.class)
public class ShadowGMSLocationUpdateListener {

    public static void provideFakeLocation(Location location) {
        GMSLocationController.locationUpdateListener.onLocationChanged(location);
    }

}
