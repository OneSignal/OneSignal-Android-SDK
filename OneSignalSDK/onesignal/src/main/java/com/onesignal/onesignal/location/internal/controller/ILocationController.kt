package com.onesignal.onesignal.location.internal.controller

import android.location.Location
import com.onesignal.onesignal.core.internal.common.events.IEventNotifier

interface ILocationController : IEventNotifier<ILocationUpdatedHandler> {
    suspend fun start(): Boolean
    suspend fun stop()

    fun getLastLocation(): Location?
}

interface ILocationUpdatedHandler {
    fun onLocationChanged(location: Location)
}
