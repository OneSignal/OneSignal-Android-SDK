package com.onesignal.location.internal.controller.impl

import android.location.Location
import android.os.Handler
import android.os.HandlerThread
import com.huawei.hmf.tasks.OnSuccessListener
import com.huawei.hms.location.FusedLocationProviderClient
import com.huawei.hms.location.LocationCallback
import com.huawei.hms.location.LocationRequest
import com.huawei.hms.location.LocationResult
import com.onesignal.common.events.EventProducer
import com.onesignal.common.threading.Waiter
import com.onesignal.common.threading.WaiterWithValue
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.location.internal.common.LocationConstants
import com.onesignal.location.internal.controller.ILocationController
import com.onesignal.location.internal.controller.ILocationUpdatedHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable

internal class HmsLocationController(
    private val _applicationService: IApplicationService,
) : ILocationController {
    private val locationHandlerThread = LocationHandlerThread()
    private val startStopMutex = Mutex()
    private val event = EventProducer<ILocationUpdatedHandler>()

    // exist after start and before stop. updates are protected by the startStopMutex
    private var hmsFusedLocationClient: FusedLocationProviderClient? = null
    private var locationUpdateListener: LocationUpdateListener? = null

    // contains the last location received from location services
    private var lastLocation: Location? = null

    override suspend fun start(): Boolean {
        var self = this
        var wasSuccessful = false

        withContext(Dispatchers.IO) {
            startStopMutex.withLock {
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

                if (lastLocation != null) {
                    event.fire { it.onLocationChanged(lastLocation!!) }
                } else {
                    var waiter = WaiterWithValue<Boolean>()
                    hmsFusedLocationClient!!.lastLocation
                        .addOnSuccessListener(
                            OnSuccessListener { location ->
                                Logging.warn("Huawei LocationServices getLastLocation returned location: $location")

                                if (location == null) {
                                    waiter.wake(false)
                                    return@OnSuccessListener
                                }

                                lastLocation = location
                                waiter.wake(true)
                            },
                        )
                        .addOnFailureListener { e ->
                            Logging.error("Huawei LocationServices getLastLocation failed!", e)
                            waiter.wake(false)
                        }
                    wasSuccessful = waiter.waitForWake()

                    if (wasSuccessful) {
                        event.fire { it.onLocationChanged(lastLocation!!) }
                        locationUpdateListener =
                            LocationUpdateListener(
                                self,
                                _applicationService,
                                hmsFusedLocationClient!!,
                            )
                    }
                }
            }
        }

        return wasSuccessful
    }

    override suspend fun stop() {
        startStopMutex.withLock {
            if (locationUpdateListener != null) {
                locationUpdateListener!!.close()
                locationUpdateListener = null
            }

            if (hmsFusedLocationClient != null) {
                hmsFusedLocationClient = null
            }

            lastLocation = null
        }
    }

    override fun getLastLocation(): Location? {
        val locationClient: FusedLocationProviderClient = hmsFusedLocationClient ?: return null

        var retVal: Location? = null

        suspendifyOnIO {
            var waiter = Waiter()
            locationClient.lastLocation
                .addOnSuccessListener(
                    OnSuccessListener { location ->
                        Logging.warn("Huawei LocationServices getLastLocation returned location: $location")

                        if (location == null) {
                            waiter.wake()
                            return@OnSuccessListener
                        }

                        retVal = location
                        waiter.wake()
                    },
                )
                .addOnFailureListener { e ->
                    Logging.error("Huawei LocationServices getLastLocation failed!", e)
                    waiter.wake()
                }
            waiter.waitForWake()
        }

        return retVal
    }

    override fun subscribe(handler: ILocationUpdatedHandler) = event.subscribe(handler)

    override fun unsubscribe(handler: ILocationUpdatedHandler) = event.unsubscribe(handler)

    override val hasSubscribers: Boolean
        get() = event.hasSubscribers

    internal class LocationUpdateListener(
        private val _parent: HmsLocationController,
        private val _applicationService: IApplicationService,
        private val huaweiFusedLocationProviderClient: FusedLocationProviderClient,
    ) : LocationCallback(), IApplicationLifecycleHandler, Closeable {
        private var hasExistingRequest = false

        init {
            _applicationService.addApplicationLifecycleHandler(this)
            refreshRequest()
        }

        override fun onFocus(firedOnSubscribe: Boolean) {
            Logging.log(LogLevel.DEBUG, "LocationUpdateListener.onFocus()")
            refreshRequest()
        }

        override fun onUnfocused() {
            Logging.log(LogLevel.DEBUG, "LocationUpdateListener.onUnfocused()")
            refreshRequest()
        }

        override fun close() {
            _applicationService.removeApplicationLifecycleHandler(this)

            if (hasExistingRequest) {
                huaweiFusedLocationProviderClient.removeLocationUpdates(this)
            }
        }

        override fun onLocationResult(locationResult: LocationResult) {
            Logging.debug("HMSLocationController onLocationResult: $locationResult")
            _parent.lastLocation = locationResult.lastLocation
        }

        private fun refreshRequest() {
            if (hasExistingRequest) {
                huaweiFusedLocationProviderClient.removeLocationUpdates(this)
            }

            var updateInterval = LocationConstants.BACKGROUND_UPDATE_TIME_MS
            if (_applicationService.isInForeground) {
                updateInterval =
                    LocationConstants.FOREGROUND_UPDATE_TIME_MS
            }
            val locationRequest =
                LocationRequest.create()
                    .setFastestInterval(updateInterval)
                    .setInterval(updateInterval)
                    .setMaxWaitTime((updateInterval * 1.5).toLong())
                    .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
            Logging.debug("HMSLocationController Huawei LocationServices requestLocationUpdates!")
            huaweiFusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                this,
                _parent.locationHandlerThread.looper,
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
