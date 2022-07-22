package com.onesignal.onesignal.location.internal

import com.onesignal.onesignal.core.internal.service.ServiceBuilder
import com.onesignal.onesignal.location.ILocationManager

object LocationModule {
    fun register(builder: ServiceBuilder) {
        builder.register<LocationManager>().provides<ILocationManager>()
    }
}