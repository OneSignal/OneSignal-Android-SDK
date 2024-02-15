package com.onesignal.location.internal.controller.impl

import android.location.Location
import android.os.Looper
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.onesignal.debug.internal.logging.Logging

internal class FusedLocationApiWrapperImpl : IFusedLocationApiWrapper {
    override fun cancelLocationUpdates(
        googleApiClient: GoogleApiClient,
        locationListener: LocationListener,
    ) {
        if (googleApiClient.isConnected) {
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, locationListener)
        } else {
            Logging.warn("GoogleApiClient is not connected. Unable to cancel location updates.")
        }
    }

    override fun requestLocationUpdates(
        googleApiClient: GoogleApiClient,
        locationRequest: LocationRequest,
        locationListener: LocationListener,
    ) {
        try {
            if (Looper.myLooper() == null) {
                Looper.prepare()
            }
            if (googleApiClient.isConnected) {
                LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, locationListener)
            }
        } catch (t: Throwable) {
            Logging.warn("FusedLocationApi.requestLocationUpdates failed!", t)
        }
    }

    override fun getLastLocation(googleApiClient: GoogleApiClient): Location? {
        if (googleApiClient.isConnected) {
            return LocationServices.FusedLocationApi.getLastLocation(googleApiClient)
        }
        return null
    }
}
