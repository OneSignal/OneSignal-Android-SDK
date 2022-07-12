package com.onesignal.onesignal.location.internal

import com.onesignal.onesignal.core.internal.service.ServiceBuilder

object LocationModule {
    fun register(builder: ServiceBuilder) {
        builder.register<LocationManager>().provides<LocationManager>()
    }
}