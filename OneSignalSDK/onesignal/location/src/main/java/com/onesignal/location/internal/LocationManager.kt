package com.onesignal.location.internal

import android.os.Build
import com.onesignal.common.AndroidUtils
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.location.ILocationManager
import com.onesignal.location.internal.capture.ILocationCapturer
import com.onesignal.location.internal.common.LocationConstants
import com.onesignal.location.internal.common.LocationUtils
import com.onesignal.location.internal.controller.ILocationController
import com.onesignal.location.internal.permissions.ILocationPermissionChangedHandler
import com.onesignal.location.internal.permissions.LocationPermissionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class LocationManager(
    private val _applicationService: IApplicationService,
    private val _capturer: ILocationCapturer,
    private val _locationController: ILocationController,
    private val _locationPermissionController: LocationPermissionController,
    private val _prefs: IPreferencesService,
) : ILocationManager, IStartableService, ILocationPermissionChangedHandler {
    private var _isShared: Boolean = _prefs.getBool(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_LOCATION_SHARED, false)!!
    override var isShared
        get() = _isShared
        set(value) {
            Logging.debug("LocationManager.setIsShared(value: $value)")
            _prefs.saveBool(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_LOCATION_SHARED, value)
            _isShared = value

            onLocationPermissionChanged(value)
        }

    override fun start() {
        _locationPermissionController.subscribe(this)
        if (LocationUtils.hasLocationPermission(_applicationService.appContext)) {
            suspendifyOnIO {
                startGetLocation()
            }
        }
    }

    override fun onLocationPermissionChanged(enabled: Boolean) {
        if (enabled) {
            suspendifyOnIO {
                startGetLocation()
            }
        }
    }

    /**
     * This method handle location and permission location flows and border cases.
     * For each flow we need to trigger location prompts listener,
     * in that way all listener will now that location request completed, even if its showing a prompt
     *
     * Cases managed:
     * - If app doesn't have location sharing activated, then location will not attributed
     * - For API less than 23, prompt permission aren't needed
     * - For API greater or equal than 23
     * - Ask for permission if needed, this will prompt PermissionActivity
     * - If permission granted, then trigger location attribution
     * - If permission denied, then trigger fail flow
     * - If location service is disable, then trigger fail flow
     * - If the user approved for location and has disable location this will continue triggering fails flows
     *
     * For all cases we are calling prompt listeners.
     */
    override suspend fun requestPermission(): Boolean {
        Logging.log(LogLevel.DEBUG, "LocationManager.requestPermission()")

        var result = false
        withContext(Dispatchers.Main) {
            if (!isShared) {
                Logging.warn("Requesting location permission, but location sharing must also be enabled by setting isShared to true")
            }

            val hasFinePermissionGranted =
                AndroidUtils.hasPermission(
                    LocationConstants.ANDROID_FINE_LOCATION_PERMISSION_STRING,
                    true,
                    _applicationService,
                )
            var hasCoarsePermissionGranted: Boolean = false
            var hasBackgroundPermissionGranted: Boolean = false

            if (!hasFinePermissionGranted) {
                hasCoarsePermissionGranted = AndroidUtils.hasPermission(LocationConstants.ANDROID_COARSE_LOCATION_PERMISSION_STRING, true, _applicationService)
                _capturer.locationCoarse = true
            }

            if (Build.VERSION.SDK_INT >= 29) {
                hasBackgroundPermissionGranted = AndroidUtils.hasPermission(LocationConstants.ANDROID_BACKGROUND_LOCATION_PERMISSION_STRING, true, _applicationService)
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                if (!hasFinePermissionGranted && !hasCoarsePermissionGranted) {
                    // Permission missing on manifest
                    Logging.error("Location permissions not added on AndroidManifest file < M")
                    return@withContext false
                }

                startGetLocation()
                result = true
            } else { // Android 6.0+
                if (!hasFinePermissionGranted) {
                    var requestPermission: String? = null
                    var permissionList =
                        AndroidUtils.filterManifestPermissions(
                            listOf(
                                LocationConstants.ANDROID_FINE_LOCATION_PERMISSION_STRING,
                                LocationConstants.ANDROID_COARSE_LOCATION_PERMISSION_STRING,
                                LocationConstants.ANDROID_BACKGROUND_LOCATION_PERMISSION_STRING,
                            ),
                            _applicationService,
                        )

                    if (permissionList.contains(LocationConstants.ANDROID_FINE_LOCATION_PERMISSION_STRING)) {
                        // ACCESS_FINE_LOCATION permission defined on Manifest, prompt for permission
                        // If permission already given prompt will return positive, otherwise will prompt again or show settings
                        requestPermission = LocationConstants.ANDROID_FINE_LOCATION_PERMISSION_STRING
                    } else if (permissionList.contains(LocationConstants.ANDROID_COARSE_LOCATION_PERMISSION_STRING)) {
                        if (!hasCoarsePermissionGranted) {
                            // ACCESS_COARSE_LOCATION permission defined on Manifest, prompt for permission
                            // If permission already given prompt will return positive, otherwise will prompt again or show settings
                            requestPermission = LocationConstants.ANDROID_COARSE_LOCATION_PERMISSION_STRING
                        } else if (Build.VERSION.SDK_INT >= 29 && permissionList.contains(LocationConstants.ANDROID_BACKGROUND_LOCATION_PERMISSION_STRING)) {
                            // ACCESS_BACKGROUND_LOCATION permission defined on Manifest, prompt for permission
                            requestPermission = LocationConstants.ANDROID_BACKGROUND_LOCATION_PERMISSION_STRING
                        }
                    } else {
                        Logging.info("Location permissions not added on AndroidManifest file >= M")
                    }

                    // We handle the following cases:
                    //  1 - If needed and available then prompt for permissions
                    //       - Request permission can be ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION
                    //  2 - If the permission were already granted then start getting location
                    //  3 - If permission wasn't granted then trigger fail flow
                    //
                    // For each case, we call the prompt handlers
                    result =
                        if (requestPermission != null) {
                            _locationPermissionController.prompt(true, requestPermission)
                        } else {
                            hasCoarsePermissionGranted
                        }
                } else if (Build.VERSION.SDK_INT >= 29 && !hasBackgroundPermissionGranted) {
                    result = backgroundLocationPermissionLogic(true)
                } else {
                    result = true
                    startGetLocation()
                }
            }
        }

        return result
    }

    /**
     * On Android 10 background location permission is needed
     * On Android 11 and greater, background location should be asked after fine and coarse permission
     * If background permission is asked at the same time as fine and coarse then both permission request are ignored
     */
    private suspend fun backgroundLocationPermissionLogic(fallbackToSettings: Boolean): Boolean {
        val hasManifestPermission =
            AndroidUtils.hasPermission(
                LocationConstants.ANDROID_BACKGROUND_LOCATION_PERMISSION_STRING,
                false,
                _applicationService,
            )

        return if (hasManifestPermission) {
            _locationPermissionController.prompt(fallbackToSettings, LocationConstants.ANDROID_BACKGROUND_LOCATION_PERMISSION_STRING)
        } else {
            // Fine permission already granted
            true
        }
    }

    // Started from this class or PermissionActivity
    private suspend fun startGetLocation() {
        if (!isShared) {
            return
        }

        Logging.debug("LocationManager.startGetLocation()") // with lastLocation: " + lastLocation)
        try {
            if (!_locationController.start()) {
                Logging.warn("LocationManager.startGetLocation: not possible, no location dependency found")
            }
        } catch (t: Throwable) {
            Logging.warn(
                "LocationManager.startGetLocation: Location permission exists but there was an error initializing: ",
                t,
            )
        }
    }
}
