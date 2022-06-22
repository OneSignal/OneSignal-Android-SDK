package com.onesignal.onesignal.internal.location

import com.onesignal.onesignal.location.ILocationManager
import com.onesignal.onesignal.logging.LogLevel
import com.onesignal.onesignal.logging.Logging

class LocationManager : ILocationManager {
    override var isLocationShared: Boolean = false

    override suspend fun requestPermission() {
        Logging.log(LogLevel.DEBUG, "promptLocationAsync()")
//        TODO("Not yet implemented")
    }
}