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

    // Controls the value returned by [requestLocationUpdates] to simulate the subscription
    // succeeding or failing (e.g. a swallowed SecurityException when permission is missing).
    var requestLocationUpdatesResult: Boolean = true

    var requestLocationUpdatesCallCount: Int = 0
        private set

    var cancelLocationUpdatesCallCount: Int = 0
        private set

    init {
        this.locations = LinkedList(locationsList)
    }

    override fun cancelLocationUpdates(
        googleApiClient: GoogleApiClient,
        locationListener: LocationListener,
    ) {
        cancelLocationUpdatesCallCount++
    }

    override fun requestLocationUpdates(
        googleApiClient: GoogleApiClient,
        locationRequest: LocationRequest,
        locationListener: LocationListener,
    ): Boolean {
        requestLocationUpdatesCallCount++
        return requestLocationUpdatesResult
    }

    override fun getLastLocation(googleApiClient: GoogleApiClient): Location? {
        return locations.remove()
    }
}
