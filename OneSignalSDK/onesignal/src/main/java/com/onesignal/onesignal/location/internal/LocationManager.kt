package com.onesignal.onesignal.location.internal

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.onesignal.onesignal.core.LogLevel
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.common.AndroidSupportV4Compat
import com.onesignal.onesignal.core.internal.common.suspendifyOnThread
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.startup.IStartableService
import com.onesignal.onesignal.location.ILocationManager
import com.onesignal.onesignal.location.internal.capture.ILocationCapturer
import com.onesignal.onesignal.location.internal.common.LocationUtils
import com.onesignal.onesignal.location.internal.controller.ILocationController
import com.onesignal.onesignal.location.internal.permissions.LocationPermissionController

internal class LocationManager(
    private val _applicationService: IApplicationService,
    private val _capturer: ILocationCapturer,
    private val _locationController: ILocationController,
    private val _locationPermissionController: LocationPermissionController
) : ILocationManager, IStartableService {

    override var isLocationShared: Boolean = false

    override fun start() {
        if (LocationUtils.hasLocationPermission(_applicationService.appContext)) {
            suspendifyOnThread {
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
    override suspend fun requestPermission(): Boolean? {
        Logging.log(LogLevel.DEBUG, "LocationManager.requestPermission()")

        if (!isLocationShared)
            return false

        var result: Boolean? = null

        var locationBackgroundPermission = PackageManager.PERMISSION_DENIED
        var locationCoarsePermission = PackageManager.PERMISSION_DENIED
        val locationFinePermission = AndroidSupportV4Compat.ContextCompat.checkSelfPermission(
            _applicationService.appContext,
            "android.permission.ACCESS_FINE_LOCATION"
        )

        if (locationFinePermission == PackageManager.PERMISSION_DENIED) {
            locationCoarsePermission = AndroidSupportV4Compat.ContextCompat.checkSelfPermission(
                _applicationService.appContext,
                "android.permission.ACCESS_COARSE_LOCATION"
            )

            _capturer.locationCoarse = true
        }

        if (Build.VERSION.SDK_INT >= 29)
            locationBackgroundPermission = AndroidSupportV4Compat.ContextCompat.checkSelfPermission(_applicationService.appContext, "android.permission.ACCESS_BACKGROUND_LOCATION")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (locationFinePermission != PackageManager.PERMISSION_GRANTED && locationCoarsePermission != PackageManager.PERMISSION_GRANTED) {
                // Permission missing on manifest
                Logging.error("Location permissions not added on AndroidManifest file < M")
                return null
            }

            startGetLocation()
            result = true
        } else { // Android 6.0+
            if (locationFinePermission != PackageManager.PERMISSION_GRANTED) {
                try {
                    var requestPermission: String? = null
                    val packageInfo: PackageInfo = _applicationService.appContext
                        .packageManager
                        .getPackageInfo(_applicationService.appContext.packageName, PackageManager.GET_PERMISSIONS)
                    val permissionList = listOf(*packageInfo.requestedPermissions)

                    if (permissionList.contains("android.permission.ACCESS_FINE_LOCATION")) {
                        // ACCESS_FINE_LOCATION permission defined on Manifest, prompt for permission
                        // If permission already given prompt will return positive, otherwise will prompt again or show settings
                        requestPermission = "android.permission.ACCESS_FINE_LOCATION"
                    } else if (permissionList.contains("android.permission.ACCESS_COARSE_LOCATION")) {
                        if (locationCoarsePermission != PackageManager.PERMISSION_GRANTED) {
                            // ACCESS_COARSE_LOCATION permission defined on Manifest, prompt for permission
                            // If permission already given prompt will return positive, otherwise will prompt again or show settings
                            requestPermission = "android.permission.ACCESS_COARSE_LOCATION"
                        } else if (Build.VERSION.SDK_INT >= 29 && permissionList.contains("android.permission.ACCESS_BACKGROUND_LOCATION")) {
                            // ACCESS_BACKGROUND_LOCATION permission defined on Manifest, prompt for permission
                            requestPermission = "android.permission.ACCESS_BACKGROUND_LOCATION"
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
                    if (requestPermission != null) {
                        result = _locationPermissionController.prompt(true, requestPermission)
                        if (result == true) {
                            startGetLocation()
                        }
                    } else if (locationCoarsePermission == PackageManager.PERMISSION_GRANTED) {
                        startGetLocation()
                        result = true
                    } else {
                        result = false
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    e.printStackTrace()
                    result = null
                }
            } else if (Build.VERSION.SDK_INT >= 29 && locationBackgroundPermission != PackageManager.PERMISSION_GRANTED) {
                result = backgroundLocationPermissionLogic()
            } else {
                startGetLocation()
                result = true
            }
        }

        // if result is null that means the user has gone to app settings and may or may not do
        // something there.  However when they come back the application will be brought into
        // focus and our application lifecycle handler will pick up any change that could have
        // occurred.
        return result
    }

    /**
     * On Android 10 background location permission is needed
     * On Android 11 and greater, background location should be asked after fine and coarse permission
     * If background permission is asked at the same time as fine and coarse then both permission request are ignored
     */
    private suspend fun backgroundLocationPermissionLogic(): Boolean? {
        try {
            var requestPermission: String? = null
            val packageInfo = _applicationService.appContext.packageManager.getPackageInfo(
                _applicationService.appContext.packageName,
                PackageManager.GET_PERMISSIONS
            )
            val permissionList = listOf(*packageInfo.requestedPermissions)
            if (permissionList.contains("android.permission.ACCESS_BACKGROUND_LOCATION")) {
                // ACCESS_BACKGROUND_LOCATION permission defined on Manifest, prompt for permission
                requestPermission = "android.permission.ACCESS_BACKGROUND_LOCATION"
            }
            val result = if (requestPermission != null) {
                _locationPermissionController.prompt(true, requestPermission)
            } else {
                // Fine permission already granted
                true
            }

            if (result == true) {
                startGetLocation()
            }

            return result
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        return false
    }

    // Started from this class or PermissionActivity
    private suspend fun startGetLocation() {
        Logging.debug("LocationController startGetLocation") // with lastLocation: " + lastLocation)
        try {
            if (!_locationController!!.start()) {
                Logging.warn("LocationController startGetLocation not possible, no location dependency found")
            }
        } catch (t: Throwable) {
            Logging.warn("Location permission exists but there was an error initializing: ", t)
        }
    }
}
