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
        try {
            if (googleApiClient.isConnected) {
                LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, locationListener)
            } else {
                Logging.warn("GoogleApiClient is not connected. Unable to cancel location updates.")
            }
        } catch (t: Throwable) {
            Logging.warn("FusedLocationApi.cancelLocationUpdates failed!", t)
        }
    }

    override fun requestLocationUpdates(
        googleApiClient: GoogleApiClient,
        locationRequest: LocationRequest,
        locationListener: LocationListener,
    ): Boolean {
        return try {
            if (Looper.myLooper() == null) {
                Looper.prepare()
            }
            if (googleApiClient.isConnected) {
                LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, locationListener)
                true
            } else {
                false
            }
        } catch (t: Throwable) {
            Logging.warn("FusedLocationApi.requestLocationUpdates failed!", t)
            false
        }
    }

    override fun getLastLocation(googleApiClient: GoogleApiClient): Location? {
        try {
            if (googleApiClient.isConnected) {
                return LocationServices.FusedLocationApi.getLastLocation(googleApiClient)
            }
        } catch (t: Throwable) {
            Logging.warn("FusedLocationApi.getLastLocation failed!", t)
        }
        return null
    }
}
