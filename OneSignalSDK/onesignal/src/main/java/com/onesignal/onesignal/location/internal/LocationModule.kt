package com.onesignal.onesignal.location.internal

import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.background.IBackgroundService
import com.onesignal.onesignal.core.internal.device.IDeviceService
import com.onesignal.onesignal.core.internal.service.ServiceBuilder
import com.onesignal.onesignal.core.internal.startup.IStartableService
import com.onesignal.onesignal.location.ILocationManager
import com.onesignal.onesignal.location.internal.background.LocationBackgroundService
import com.onesignal.onesignal.location.internal.capture.ILocationCapturer
import com.onesignal.onesignal.location.internal.capture.impl.LocationCapturer
import com.onesignal.onesignal.location.internal.permissions.LocationPermissionController
import com.onesignal.onesignal.location.internal.controller.ILocationController
import com.onesignal.onesignal.location.internal.controller.impl.GmsLocationController
import com.onesignal.onesignal.location.internal.controller.impl.HmsLocationController
import com.onesignal.onesignal.location.internal.controller.impl.NullLocationController
import com.onesignal.onesignal.location.internal.preferences.ILocationPreferencesService
import com.onesignal.onesignal.location.internal.preferences.impl.LocationPreferencesService

object LocationModule {
    fun register(builder: ServiceBuilder) {
        builder.register<LocationPermissionController>()
               .provides<LocationPermissionController>()
               .provides<IStartableService>()

        builder.register {
            val deviceService = it.getService(IDeviceService::class.java)
            val service = if (deviceService.isGooglePlayServicesAvailable) {
                GmsLocationController(it.getService(IApplicationService::class.java))
            } else if(deviceService.isHMSAvailable) {
                HmsLocationController(it.getService(IApplicationService::class.java))
            } else {
                NullLocationController()
            }
            return@register service
        }.provides<ILocationController>()

        builder.register<LocationPreferencesService>().provides<ILocationPreferencesService>()
        builder.register<LocationCapturer>().provides<ILocationCapturer>()
        builder.register<LocationBackgroundService>().provides<IBackgroundService>()
        builder.register<LocationManager>()
               .provides<ILocationManager>()
               .provides<IStartableService>()
    }
}