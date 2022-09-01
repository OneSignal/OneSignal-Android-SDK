package com.onesignal.location.internal.controller.impl

import android.location.Location
import android.os.Handler
import android.os.HandlerThread
import com.huawei.hmf.tasks.OnSuccessListener
import com.huawei.hms.location.FusedLocationProviderClient
import com.huawei.hms.location.LocationCallback
import com.huawei.hms.location.LocationRequest
import com.huawei.hms.location.LocationResult
import com.onesignal.core.LogLevel
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.common.events.EventProducer
import com.onesignal.core.internal.logging.Logging
import com.onesignal.location.internal.common.LocationConstants
import com.onesignal.location.internal.controller.ILocationController
import com.onesignal.location.internal.controller.ILocationUpdatedHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable

// TODO: Implement
class HmsLocationController(
    private val _applicationService: IApplicationService
) : ILocationController {
    private val _locationHandlerThread = LocationHandlerThread()
    private val _startStopMutex = Mutex()
    private val _event = EventProducer<ILocationUpdatedHandler>()

    // exist after start and before stop. updates are protected by the startStopMutex
    private var hmsFusedLocationClient: FusedLocationProviderClient? = null
    private var locationUpdateListener: LocationUpdateListener? = null

    // contains the last location received from location services
    private var _lastLocation: Location? = null

    override suspend fun start(): Boolean {
        var self = this
        var wasSuccessful = false

        withContext(Dispatchers.IO) {
            _startStopMutex.withLock {
                if (hmsFusedLocationClient == null) {
                    try {
                        hmsFusedLocationClient =
                            com.huawei.hms.location.LocationServices.getFusedLocationProviderClient(_applicationService.appContext)
                    } catch (e: Exception) {
                        Logging.error("Huawei LocationServices getFusedLocationProviderClient failed! $e")
                        wasSuccessful = false
                        return@withLock
                    }
                }

                if (_lastLocation != null)
                    _event.fire { it.onLocationChanged(_lastLocation!!) }
                else {
                    var channel = Channel<Boolean>()
                    hmsFusedLocationClient!!.lastLocation
                        .addOnSuccessListener(
                            OnSuccessListener { location ->
                                Logging.warn("Huawei LocationServices getLastLocation returned location: $location")

                                if (location == null) {
                                    runBlocking { channel.send(false) }
                                    return@OnSuccessListener
                                }

                                _lastLocation = location
                                runBlocking { channel.send(true) }
                            }
                        )
                        .addOnFailureListener { e ->
                            Logging.error("Huawei LocationServices getLastLocation failed!", e)
                            runBlocking { channel.send(false) }
                        }
                    wasSuccessful = channel.receive()

                    if (wasSuccessful) {
                        _event.fire { it.onLocationChanged(_lastLocation!!) }
                        locationUpdateListener = LocationUpdateListener(
                            self,
                            _applicationService,
                            hmsFusedLocationClient!!
                        )
                    }
                }
            }
        }

        return wasSuccessful
    }

    override suspend fun stop() {
        _startStopMutex.withLock {
            if (locationUpdateListener != null) {
                locationUpdateListener!!.close()
                locationUpdateListener = null
            }

            if (hmsFusedLocationClient != null) {
                hmsFusedLocationClient = null
            }

            _lastLocation = null
        }
    }

    override fun getLastLocation(): Location? {
        val locationClient: FusedLocationProviderClient = hmsFusedLocationClient ?: return null

        var retVal: Location? = null

        runBlocking {
            var channel = Channel<Any?>()
            locationClient.lastLocation
                .addOnSuccessListener(
                    OnSuccessListener { location ->
                        Logging.warn("Huawei LocationServices getLastLocation returned location: $location")

                        if (location == null) {
                            runBlocking { channel.send(null) }
                            return@OnSuccessListener
                        }

                        retVal = location
                        runBlocking { channel.send(null) }
                    }
                )
                .addOnFailureListener { e ->
                    Logging.error("Huawei LocationServices getLastLocation failed!", e)
                    runBlocking { channel.send(null) }
                }
            channel.receive()
        }

        return retVal
    }

    override fun subscribe(handler: ILocationUpdatedHandler) = _event.subscribe(handler)
    override fun unsubscribe(handler: ILocationUpdatedHandler) = _event.unsubscribe(handler)

    internal class LocationUpdateListener(
        private val _parent: HmsLocationController,
        private val _applicationService: IApplicationService,
        private val huaweiFusedLocationProviderClient: FusedLocationProviderClient
    ) : LocationCallback(), IApplicationLifecycleHandler, Closeable {

        private var hasExistingRequest = false

        init {
            _applicationService.addApplicationLifecycleHandler(this)
            refreshRequest()
        }

        override fun onFocus() {
            Logging.log(LogLevel.DEBUG, "LocationUpdateListener.onFocus()")
            refreshRequest()
        }

        override fun onUnfocused() {
            Logging.log(LogLevel.DEBUG, "LocationUpdateListener.onUnfocused()")
            refreshRequest()
        }

        override fun close() {
            _applicationService.removeApplicationLifecycleHandler(this)

            if (hasExistingRequest)
                huaweiFusedLocationProviderClient.removeLocationUpdates(this)
        }

        override fun onLocationResult(locationResult: LocationResult) {
            Logging.debug("HMSLocationController onLocationResult: $locationResult")
            _parent._lastLocation = locationResult.lastLocation
        }

        private fun refreshRequest() {
            if (hasExistingRequest) {
                huaweiFusedLocationProviderClient.removeLocationUpdates(this)
            }

            var updateInterval = LocationConstants.BACKGROUND_UPDATE_TIME_MS
            if (_applicationService.isInForeground) updateInterval =
                LocationConstants.FOREGROUND_UPDATE_TIME_MS
            val locationRequest = LocationRequest.create()
                .setFastestInterval(updateInterval)
                .setInterval(updateInterval)
                .setMaxWaitTime((updateInterval * 1.5).toLong())
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
            Logging.debug("HMSLocationController Huawei LocationServices requestLocationUpdates!")
            huaweiFusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                this,
                _parent._locationHandlerThread.looper
            )
            hasExistingRequest = true
        }
    }

    class LocationHandlerThread internal constructor() :
        HandlerThread("OSH_LocationHandlerThread") {
        var mHandler: Handler

        init {
            start()
            mHandler = Handler(looper)
        }
    }
}
