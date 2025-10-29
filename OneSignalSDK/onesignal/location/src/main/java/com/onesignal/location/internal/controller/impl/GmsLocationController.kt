package com.onesignal.location.internal.controller.impl

import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.onesignal.common.events.EventProducer
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.location.internal.common.LocationConstants
import com.onesignal.location.internal.controller.ILocationController
import com.onesignal.location.internal.controller.ILocationUpdatedHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.Closeable

internal class GmsLocationController(
    private val _applicationService: IApplicationService,
    private val _fusedLocationApiWrapper: IFusedLocationApiWrapper,
) : ILocationController {
    private val locationHandlerThread = LocationHandlerThread()
    private val startStopMutex = Mutex()
    private val event = EventProducer<ILocationUpdatedHandler>()

    // exist after start and before stop. updates are protected by the startStopMutex
    private var googleApiClient: GoogleApiClientCompatProxy? = null
    private var locationUpdateListener: LocationUpdateListener? = null

    // contains the last location received from location services
    private var lastLocation: Location? = null

    override suspend fun start(): Boolean {
        var self = this
        var wasSuccessful = false

        withContext(Dispatchers.IO) {
            startStopMutex.withLock {
                if (googleApiClient != null) {
                    if (lastLocation != null) {
                        event.fire { it.onLocationChanged(lastLocation!!) }
                    } else {
                        val localLastLocation = getLastLocation()
                        if (localLastLocation != null) {
                            setLocationAndFire(localLastLocation)
                        }
                    }
                    wasSuccessful = true
                } else {
                    try {
                        withTimeout(API_FALLBACK_TIME.toLong()) {
                            val googleApiClientListener = GoogleApiClientListener(self)
                            val googleApiClient =
                                GoogleApiClient.Builder(_applicationService.appContext)
                                    .addApi(LocationServices.API)
                                    .addConnectionCallbacks(googleApiClientListener)
                                    .addOnConnectionFailedListener(googleApiClientListener)
                                    .setHandler(locationHandlerThread.mHandler)
                                    .build()
                            var proxyGoogleApiClient = GoogleApiClientCompatProxy(googleApiClient)

                            // TODO: google api client has a blocking connect with timeout, use that instead of our withTimeout?
                            var result = proxyGoogleApiClient.blockingConnect()

                            if (result?.isSuccess == true) {
                                if (lastLocation == null) {
                                    var lastLocation = _fusedLocationApiWrapper.getLastLocation(googleApiClient)
                                    if (lastLocation != null) {
                                        setLocationAndFire(lastLocation)
                                    }
                                }

                                // only after the connect do we save
                                self.locationUpdateListener = LocationUpdateListener(_applicationService, self, proxyGoogleApiClient.realInstance, _fusedLocationApiWrapper)
                                self.googleApiClient = proxyGoogleApiClient
                                wasSuccessful = true
                            } else {
                                Logging.debug(
                                    "GMSLocationController connection to GoogleApiService failed: (${result?.errorCode}) ${result?.errorMessage}",
                                )
                            }
                        }
                    } catch (e: TimeoutCancellationException) {
                        Logging.warn(
                            "Location permission exists but GoogleApiClient timed out. Maybe related to mismatch google-play aar versions.",
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

            if (googleApiClient != null) {
                googleApiClient!!.disconnect()
                googleApiClient = null
            }

            lastLocation = null
        }
    }

    override fun getLastLocation(): Location? {
        val apiInstance = googleApiClient?.realInstance ?: return null
        return _fusedLocationApiWrapper.getLastLocation(apiInstance)
    }

    override fun subscribe(handler: ILocationUpdatedHandler) = event.subscribe(handler)

    override fun unsubscribe(handler: ILocationUpdatedHandler) = event.unsubscribe(handler)

    override val hasSubscribers: Boolean
        get() = event.hasSubscribers

    private fun setLocationAndFire(location: Location) {
        Logging.debug("GMSLocationController lastLocation: $lastLocation")

        lastLocation = location

        event.fire { it.onLocationChanged(location) }
    }

    private class GoogleApiClientListener(private val _parent: GmsLocationController) : GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        override fun onConnected(bundle: Bundle?) {
            Logging.debug("GMSLocationController GoogleApiClientListener onConnected")
        }

        override fun onConnectionSuspended(i: Int) {
            Logging.debug("GMSLocationController GoogleApiClientListener onConnectionSuspended i: $i")
            // the assumption is that GMS will reconnect if it can, so we take no action.
        }

        override fun onConnectionFailed(connectionResult: ConnectionResult) {
            Logging.debug("GMSLocationController GoogleApiClientListener onConnectionSuspended connectionResult: $connectionResult")

            suspendifyOnIO {
                _parent.stop()
            }
        }
    }

    class LocationUpdateListener(
        private val _applicationService: IApplicationService,
        private val _parent: GmsLocationController,
        private val googleApiClient: GoogleApiClient,
        private val _fusedLocationApiWrapper: IFusedLocationApiWrapper,
    ) : LocationListener, IApplicationLifecycleHandler, Closeable {
        private var hasExistingRequest = false

        init {
            if (!googleApiClient.isConnected) {
                throw Exception("googleApiClient not connected, cannot listen!")
            }

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
                _fusedLocationApiWrapper.cancelLocationUpdates(googleApiClient, this)
            }
        }

        private fun refreshRequest() {
            if (!googleApiClient.isConnected) {
                Logging.warn("Attempt to refresh location request but not currently connected!")
                return
            }

            if (hasExistingRequest) {
                _fusedLocationApiWrapper.cancelLocationUpdates(googleApiClient, this)
            }

            val updateInterval =
                if (_applicationService.isInForeground) {
                    LocationConstants.FOREGROUND_UPDATE_TIME_MS
                } else {
                    LocationConstants.BACKGROUND_UPDATE_TIME_MS
                }

            val locationRequest =
                LocationRequest.create()
                    .setFastestInterval(updateInterval)
                    .setInterval(updateInterval)
                    .setMaxWaitTime((updateInterval * 1.5).toLong())
                    .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
            Logging.debug("GMSLocationController GoogleApiClient requestLocationUpdates!")
            _fusedLocationApiWrapper.requestLocationUpdates(googleApiClient, locationRequest, this)
            hasExistingRequest = true
        }

        override fun onLocationChanged(location: Location) {
            Logging.debug("GMSLocationController onLocationChanged: $location")
            _parent.setLocationAndFire(location)
        }
    }

    protected class LocationHandlerThread internal constructor() :
        HandlerThread("OSH_LocationHandlerThread") {
        var mHandler: Handler

        init {
            start()
            mHandler = Handler(looper)
        }
    }

    companion object {
        val API_FALLBACK_TIME = 30000
    }
}
