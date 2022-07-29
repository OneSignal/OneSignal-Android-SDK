package com.onesignal.onesignal.location.internal

import com.onesignal.onesignal.location.ILocationManager
import com.onesignal.onesignal.core.LogLevel
import com.onesignal.onesignal.core.internal.logging.Logging

class LocationManager : ILocationManager {
    override var isLocationShared: Boolean = false

    override suspend fun requestPermission() {
        Logging.log(LogLevel.DEBUG, "LocationManager.promptLocationAsync()")
//        TODO("Not yet implemented")
    }
}