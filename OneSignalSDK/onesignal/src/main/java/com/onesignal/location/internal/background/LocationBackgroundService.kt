package com.onesignal.location.internal.background

import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.background.IBackgroundService
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.time.ITime
import com.onesignal.location.ILocationManager
import com.onesignal.location.internal.capture.ILocationCapturer
import com.onesignal.location.internal.common.LocationConstants
import com.onesignal.location.internal.common.LocationUtils
import com.onesignal.location.internal.preferences.ILocationPreferencesService

internal class LocationBackgroundService(
    private val _applicationService: IApplicationService,
    private val _locationManager: ILocationManager,
    private val _prefs: ILocationPreferencesService,
    private val _capturer: ILocationCapturer,
    private val _time: ITime
) : IBackgroundService {
    override val scheduleBackgroundRunIn: Long?
        get() {
            if (!_locationManager.isLocationShared) {
                Logging.debug("LocationController scheduleUpdate not possible, location shared not enabled")
                return null
            }

            if (!LocationUtils.hasLocationPermission(_applicationService.appContext)) {
                Logging.debug("LocationController scheduleUpdate not possible, location permission not enabled")
                return null
            }

            val lastTime: Long = _time.currentTimeMillis - _prefs.lastLocationTime
            val minTime = 1000 * LocationConstants.TIME_BACKGROUND_SEC
            val scheduleTime = minTime - lastTime
            return scheduleTime
        }

    override suspend fun backgroundRun() {
        _capturer.captureLastLocation()
    }
}
