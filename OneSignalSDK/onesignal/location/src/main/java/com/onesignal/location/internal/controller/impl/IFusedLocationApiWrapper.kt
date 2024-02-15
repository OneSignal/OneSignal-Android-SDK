package com.onesignal.location.internal.controller.impl

import android.location.Location
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest

internal interface IFusedLocationApiWrapper {
    fun cancelLocationUpdates(
        googleApiClient: GoogleApiClient,
        locationListener: LocationListener,
    )

    fun requestLocationUpdates(
        googleApiClient: GoogleApiClient,
        locationRequest: LocationRequest,
        locationListener: LocationListener,
    )

    fun getLastLocation(googleApiClient: GoogleApiClient): Location?
}
