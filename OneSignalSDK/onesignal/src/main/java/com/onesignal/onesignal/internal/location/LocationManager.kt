package com.onesignal.onesignal.internal.location

import com.onesignal.onesignal.internal.application.IApplicationLifecycleHandler
import com.onesignal.onesignal.internal.application.IApplicationService
import com.onesignal.onesignal.internal.device.IDeviceService
import com.onesignal.onesignal.location.ILocationManager
import com.onesignal.onesignal.logging.LogLevel
import com.onesignal.onesignal.logging.Logging

class LocationManager(
    private val _applicationService: IApplicationService,
    private val _deviceService: IDeviceService) : ILocationManager, IApplicationLifecycleHandler {

    private var _locationController: ILocationController? = null

    override var isLocationShared: Boolean = false

    fun start() {
        _locationController = if (_deviceService.isGooglePlayServicesAvailable) {
            GmsLocationController()
        } else if(_deviceService.isHMSAvailable) {
            HmsLocationController()
        } else {
            NullLocationController()
        }

        _applicationService.addApplicationLifecycleHandler(this)
    }

    override suspend fun requestPermission() {
        Logging.log(LogLevel.DEBUG, "LocationManager.promptLocationAsync()")
//        TODO("Not yet implemented")
    }

    override fun onFocus() {
        Logging.log(LogLevel.DEBUG, "LocationManager.onFocus()")
       _locationController?.onFocusChange()
    }

    override fun onUnfocused() {
        Logging.log(LogLevel.DEBUG, "LocationManager.onUnfocused()")
        _locationController?.onFocusChange()
    }
}