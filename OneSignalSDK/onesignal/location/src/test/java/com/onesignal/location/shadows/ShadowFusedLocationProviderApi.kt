/**
 * Modified MIT License
 *
 * Copyright 2017 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.onesignal.location.shadows

import android.app.PendingIntent
import android.location.Location
import android.os.Looper
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderApi
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.TimeUnit

class ShadowFusedLocationProviderApi : FusedLocationProviderApi {

    val pendingResult = object : PendingResult<Status>() {
        override fun await(): Status = Status.RESULT_SUCCESS
        override fun await(p0: Long, p1: TimeUnit): Status = Status.RESULT_SUCCESS
        override fun cancel() { }
        override fun isCanceled(): Boolean = false
        override fun setResultCallback(p0: ResultCallback<in Status>) { }
        override fun setResultCallback(p0: ResultCallback<in Status>, p1: Long, p2: TimeUnit) {}
    }
    override fun getLastLocation(p0: GoogleApiClient): Location {
        if (locations != null) {
            val location = locations!![index]
            index++
            if (index >= locations!!.count()) {
                index = 0
            }

            return location
        }

        val location = Location("TEST_PROVIDER")
        location.latitude = 123.45
        location.longitude = 678.91
        return location
    }

    override fun getLocationAvailability(p0: GoogleApiClient): LocationAvailability = throw Exception()
    override fun requestLocationUpdates(p0: GoogleApiClient, p1: LocationRequest, p2: LocationListener): PendingResult<Status> = pendingResult
    override fun requestLocationUpdates(p0: GoogleApiClient, p1: LocationRequest, p2: LocationListener, p3: Looper): PendingResult<Status> = pendingResult
    override fun requestLocationUpdates(p0: GoogleApiClient, p1: LocationRequest, p2: LocationCallback, p3: Looper): PendingResult<Status> = pendingResult
    override fun requestLocationUpdates(p0: GoogleApiClient, p1: LocationRequest, p2: PendingIntent): PendingResult<Status> = pendingResult
    override fun removeLocationUpdates(p0: GoogleApiClient, p1: LocationListener): PendingResult<Status> = pendingResult
    override fun removeLocationUpdates(p0: GoogleApiClient, p1: PendingIntent): PendingResult<Status> = pendingResult
    override fun removeLocationUpdates(p0: GoogleApiClient, p1: LocationCallback): PendingResult<Status> = pendingResult
    override fun setMockMode(p0: GoogleApiClient, p1: Boolean): PendingResult<Status> = pendingResult
    override fun setMockLocation(p0: GoogleApiClient, p1: Location): PendingResult<Status> = pendingResult
    override fun flushLocations(p0: GoogleApiClient): PendingResult<Status> = pendingResult

    companion object {
        private var locations: List<Location>? = null
        private var index: Int = 0
        fun injectToLocationServices(locations: List<Location>) {
            this.index = 0
            this.locations = locations
            val currentFused = LocationServices.FusedLocationApi
            val newFused = ShadowFusedLocationProviderApi()

            val field = LocationServices::class.java.getDeclaredField("FusedLocationApi")
            field.isAccessible = true

            val modifiersField: Field = Field::class.java.getDeclaredField("modifiers")
            modifiersField.isAccessible = true
            modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())

            field.set(null, newFused)
        }
    }
}
