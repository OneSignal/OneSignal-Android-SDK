package com.onesignal.location.internal.controller

import android.location.Location
import com.onesignal.core.internal.common.events.IEventNotifier

interface ILocationController : IEventNotifier<ILocationUpdatedHandler> {
    suspend fun start(): Boolean
    suspend fun stop()

    fun getLastLocation(): Location?
}

interface ILocationUpdatedHandler {
    fun onLocationChanged(location: Location)
}
