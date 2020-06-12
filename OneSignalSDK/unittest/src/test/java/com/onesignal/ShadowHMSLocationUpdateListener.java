package com.onesignal;

import com.huawei.hms.location.HWLocation;
import com.huawei.hms.location.LocationResult;

import org.robolectric.annotation.Implements;

import java.util.ArrayList;
import java.util.List;

@Implements(HMSLocationController.LocationUpdateListener.class)
public class ShadowHMSLocationUpdateListener {

    public static void provideFakeLocation_Huawei(HWLocation location) {
        List<HWLocation> locations = new ArrayList<>();
        locations.add(location);
        LocationResult locationResult = LocationResult.create(locations);
        HMSLocationController.locationUpdateListener.onLocationResult(locationResult);
    }
}
