package com.onesignal.location.internal.controller

import android.location.Location
import com.onesignal.common.events.IEventNotifier

internal interface ILocationController : IEventNotifier<ILocationUpdatedHandler> {
    suspend fun start(): Boolean
    suspend fun stop()

    fun getLastLocation(): Location?
}

internal interface ILocationUpdatedHandler {
    fun onLocationChanged(location: Location)
}
