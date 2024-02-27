package com.onesignal.location.mocks

import android.location.Location
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.onesignal.location.internal.controller.impl.IFusedLocationApiWrapper
import java.util.LinkedList
import java.util.Queue

internal class FusedLocationApiWrapperMock(locationsList: List<Location>) : IFusedLocationApiWrapper {
    private val locations: Queue<Location>

    init {
        this.locations = LinkedList(locationsList)
    }

    override fun cancelLocationUpdates(
        googleApiClient: GoogleApiClient,
        locationListener: LocationListener,
    ) {}

    override fun requestLocationUpdates(
        googleApiClient: GoogleApiClient,
        locationRequest: LocationRequest,
        locationListener: LocationListener,
    ) {}

    override fun getLastLocation(googleApiClient: GoogleApiClient): Location? {
        return locations.remove()
    }
}
