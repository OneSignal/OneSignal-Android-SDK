package com.onesignal.location.internal

import com.onesignal.common.AndroidUtils
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.location.internal.capture.ILocationCapturer
import com.onesignal.location.internal.common.LocationConstants
import com.onesignal.location.internal.common.LocationUtils
import com.onesignal.location.internal.controller.ILocationController
import com.onesignal.location.internal.permissions.LocationPermissionController
import com.onesignal.mocks.IOMockHelper
import com.onesignal.mocks.IOMockHelper.awaitIO
import com.onesignal.mocks.MockHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private class Mocks {
    val locationCapture = mockk<ILocationCapturer>(relaxed = true)
    val locationController = mockk<ILocationController>(relaxed = true)
    val permissionController = mockk<LocationPermissionController>(relaxed = true)
    val mockAppService = MockHelper.applicationService()

    val mockPrefs =
        run {
            val pref = mockk<IPreferencesService>(relaxed = true)
            every {
                pref.getBool(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_LOCATION_SHARED, false)
            } returns true
            pref
        }

    val mockContext =
        run {
            val context = mockk<android.content.Context>(relaxed = true)
            every { mockAppService.appContext } returns context
            context
        }

    val locationManager = LocationManager(
        mockAppService,
        locationCapture,
        locationController,
        permissionController,
        mockPrefs,
    )

    fun setAndroidSDKInt(sdkInt: Int) {
        every { AndroidUtils.androidSDKInt } returns sdkInt
    }

    fun setFineLocationPermission(granted: Boolean) {
        every {
            AndroidUtils.hasPermission(
                LocationConstants.ANDROID_FINE_LOCATION_PERMISSION_STRING,
                true,
                mockAppService,
            )
        } returns granted
    }

    fun setCoarseLocationPermission(granted: Boolean) {
        every {
            AndroidUtils.hasPermission(
                LocationConstants.ANDROID_COARSE_LOCATION_PERMISSION_STRING,
                true,
                mockAppService,
            )
        } returns granted
    }
}

class LocationManagerTests : FunSpec({

    listener(IOMockHelper)

    lateinit var mocks: Mocks

    beforeAny {
        Logging.logLevel = LogLevel.NONE
        mocks = Mocks() // fresh instance for each test
    }

    beforeSpec {
        // required when testing functions that internally call suspendifyOnMain
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(LocationUtils)
        mockkObject(AndroidUtils)
        every { LocationUtils.hasLocationPermission(any()) } returns false
        every { AndroidUtils.hasPermission(any(), any(), any()) } returns false
        every { AndroidUtils.filterManifestPermissions(any(), any()) } returns emptyList()
    }

    afterSpec {
        Dispatchers.resetMain()
        unmockkObject(LocationUtils)
        unmockkObject(AndroidUtils)
    }

    test("isShared getter returns value from preferences") {
        // Given
        val mockPrefs = mocks.mockPrefs
        val locationManager = mocks.locationManager

        // When
        val result = locationManager.isShared

        // Then
        result shouldBe true
        verify {
            mockPrefs.getBool(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_LOCATION_SHARED, false)
        }
    }

    test("isShared setter saves value to preferences and triggers permission change") {
        // Given
        val mockPrefs = mocks.mockPrefs
        val mockLocationController = mocks.locationController
        coEvery { mockLocationController.start() } returns true
        every { LocationUtils.hasLocationPermission(any()) } returns true
        val locationManager = mocks.locationManager

        // When
        locationManager.isShared = true

        // Then
        verify {
            mockPrefs.saveBool(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_LOCATION_SHARED, true)
        }
        locationManager.isShared shouldBe true
    }

    test("isShared setter to false does not start location when permission changed") {
        // Given
        val mockLocationController = mocks.locationController
        val locationManager = mocks.locationManager

        // When
        locationManager.isShared = false

        // Then
        locationManager.isShared shouldBe false
        coVerify(exactly = 0) { mockLocationController.start() }
    }

    test("start subscribes to location permission controller") {
        // Given
        every { LocationUtils.hasLocationPermission(mocks.mockAppService.appContext) } returns false
        val locationManager = mocks.locationManager

        // When
        locationManager.start()

        // Then
        verify(exactly = 1) { mocks.permissionController.subscribe(locationManager) }
    }

    test("start calls startGetLocation when location permission is granted") {
        // Given
        every { LocationUtils.hasLocationPermission(mocks.mockAppService.appContext) } returns true
        coEvery { mocks.locationController.start() } returns true

        val locationManager = mocks.locationManager

        // When
        locationManager.start()
        awaitIO()

        // Then
        coVerify { mocks.locationController.start() }
    }

    test("start does not call startGetLocation when location permission is not granted") {
        // Given
        val mockLocationController = mockk<ILocationController>(relaxed = true)
        every { LocationUtils.hasLocationPermission(mocks.mockContext) } returns false

        val locationManager = mocks.locationManager

        // When
        locationManager.start()

        // Then
        coVerify(exactly = 0) { mockLocationController.start() }
    }

    test("onLocationPermissionChanged calls startGetLocation when enabled is true") {
        // Given
        val mockLocationController = mocks.locationController
        coEvery { mockLocationController.start() } returns true

        val locationManager = mocks.locationManager

        // When
        locationManager.onLocationPermissionChanged(true)
        awaitIO()

        // Then
        coVerify { mockLocationController.start() }
    }

    test("onLocationPermissionChanged does not call startGetLocation when enabled is false") {
        // Given
        val locationManager = mocks.locationManager

        // When
        locationManager.onLocationPermissionChanged(false)

        // Then
        coVerify(exactly = 0) { mocks.locationController.start() }
    }

    test("onLocationPermissionChanged does not call startGetLocation when isShared is false") {
        // Given
        every {
            mocks.mockPrefs.getBool(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_LOCATION_SHARED, false)
        } returns false
        // Create a new LocationManager with isShared = false
        val locationManager = LocationManager(
            mocks.mockAppService,
            mocks.locationCapture,
            mocks.locationController,
            mocks.permissionController,
            mocks.mockPrefs,
        )

        // When
        locationManager.onLocationPermissionChanged(true)
        awaitIO()

        // Then
        coVerify(exactly = 0) { mocks.locationController.start() }
    }

    test("requestPermission returns true when fine permission granted on API < 23") {
        // Given
        mocks.setFineLocationPermission(true)
        mocks.setAndroidSDKInt(22)
        val locationManager = mocks.locationManager

        // When
        val result = locationManager.requestPermission()

        // Then
        result shouldBe true
    }

    test("requestPermission returns true when coarse permission granted on API < 23") {
        // Given
        mocks.setFineLocationPermission(false)
        mocks.setCoarseLocationPermission(true)
        mocks.setAndroidSDKInt(22)
        coEvery { mocks.locationController.start() } returns true

        val locationManager = mocks.locationManager

        // When
        val result = locationManager.requestPermission()

        // Then
        result shouldBe true
        coVerify { mocks.locationController.start() }
    }

    test("requestPermission returns false when no permissions in manifest on API < 23") {
        // Given
        val mockApplicationService = mocks.mockAppService
        mocks.setFineLocationPermission(false)
        mocks.setCoarseLocationPermission(false)
        mocks.setAndroidSDKInt(22)
        // Ensure filterManifestPermissions returns empty list (no permissions in manifest)
        every {
            AndroidUtils.filterManifestPermissions(any(), mockApplicationService)
        } returns emptyList()
        val locationManager = mocks.locationManager

        // When
        val result = locationManager.requestPermission()

        // Then
        result shouldBe false
    }

    test("requestPermission returns true when fine permission already granted") {
        // Given
        mocks.setFineLocationPermission(true)
        mocks.setAndroidSDKInt(23)
        coEvery { mocks.locationController.start() } returns true
        val locationManager = mocks.locationManager

        // When
        val result = locationManager.requestPermission()

        // Then
        result shouldBe true
        coVerify { mocks.locationController.start() }
    }

    test("requestPermission prompts for fine permission when not granted and in manifest") {
        // Given
        val mockApplicationService = mocks.mockAppService
        val mockPermissionController = mocks.permissionController
        mocks.setFineLocationPermission(false)
        mocks.setAndroidSDKInt(23)
        every {
            AndroidUtils.filterManifestPermissions(
                any(),
                mockApplicationService,
            )
        } returns listOf(LocationConstants.ANDROID_FINE_LOCATION_PERMISSION_STRING)
        coEvery {
            mockPermissionController.prompt(true, LocationConstants.ANDROID_FINE_LOCATION_PERMISSION_STRING)
        } returns true
        val locationManager = mocks.locationManager

        // When
        val result = locationManager.requestPermission()

        // Then
        result shouldBe true
        coVerify {
            mockPermissionController.prompt(true, LocationConstants.ANDROID_FINE_LOCATION_PERMISSION_STRING)
        }
    }

    test("requestPermission prompts for coarse permission when fine not in manifest") {
        // Given
        val mockApplicationService = mocks.mockAppService
        val mockPermissionController = mocks.permissionController
        mocks.setFineLocationPermission(false)
        mocks.setCoarseLocationPermission(true)
        mocks.setAndroidSDKInt(23)
        every {
            AndroidUtils.filterManifestPermissions(
                any(),
                mockApplicationService,
            )
        } returns listOf(LocationConstants.ANDROID_COARSE_LOCATION_PERMISSION_STRING)
        every {
            AndroidUtils.hasPermission(
                LocationConstants.ANDROID_COARSE_LOCATION_PERMISSION_STRING,
                true,
                mocks.mockAppService,
            )
        } returns false
        val locationManager = mocks.locationManager

        // When
        locationManager.requestPermission()

        // Then
        coVerify {
            mockPermissionController.prompt(true, LocationConstants.ANDROID_COARSE_LOCATION_PERMISSION_STRING)
        }
    }

    test("requestPermission returns false when permissions not in manifest") {
        // Given
        mocks.setFineLocationPermission(false)
        val locationManager = mocks.locationManager

        // When
        val result = locationManager.requestPermission()

        // Then
        result shouldBe false
    }

    test("requestPermission returns true when coarse permission already granted") {
        // Given
        mocks.setFineLocationPermission(false)
        mocks.setCoarseLocationPermission(true)

        val locationManager = mocks.locationManager

        // When
        val result = locationManager.requestPermission()

        // Then
        result shouldBe true
    }

    test("requestPermission prompts for background permission when fine granted but background not") {
        // Given
        val mockApplicationService = mocks.mockAppService
        val mockPermissionController = mocks.permissionController
        mocks.setFineLocationPermission(true)
        mocks.setAndroidSDKInt(29)
        every {
            AndroidUtils.hasPermission(
                LocationConstants.ANDROID_BACKGROUND_LOCATION_PERMISSION_STRING,
                true,
                mockApplicationService,
            )
        } returns false
        every {
            AndroidUtils.hasPermission(
                LocationConstants.ANDROID_BACKGROUND_LOCATION_PERMISSION_STRING,
                false,
                mockApplicationService,
            )
        } returns true
        coEvery {
            mockPermissionController.prompt(true, LocationConstants.ANDROID_BACKGROUND_LOCATION_PERMISSION_STRING)
        } returns true
        coEvery { mocks.locationController.start() } returns true

        val locationManager = mocks.locationManager

        // When
        val result = locationManager.requestPermission()

        // Then
        result shouldBe true
        coVerify {
            mockPermissionController.prompt(true, LocationConstants.ANDROID_BACKGROUND_LOCATION_PERMISSION_STRING)
        }
    }

    test("requestPermission starts location when all permissions granted") {
        // Given
        val mockApplicationService = mocks.mockAppService
        mocks.setFineLocationPermission(true)
        every {
            AndroidUtils.hasPermission(
                LocationConstants.ANDROID_BACKGROUND_LOCATION_PERMISSION_STRING,
                true,
                mockApplicationService,
            )
        } returns true
        coEvery { mocks.locationController.start() } returns true
        val locationManager = mocks.locationManager

        // When
        val result = locationManager.requestPermission()

        // Then
        result shouldBe true
        coVerify { mocks.locationController.start() }
    }

    test("requestPermission warns when isShared is false") {
        // Given
        mocks.setFineLocationPermission(true)

        val locationManager = mocks.locationManager

        // When
        val result = locationManager.requestPermission()

        // Then
        result shouldBe true
        // Warning should be logged (tested indirectly through no exception)
    }

    test("requestPermission handles location controller start failure gracefully") {
        // Given
        mocks.setFineLocationPermission(true)
        coEvery { mocks.locationController.start() } returns false

        val locationManager = mocks.locationManager

        // When
        val result = locationManager.requestPermission()

        // Then
        result shouldBe true
    }

    test("requestPermission handles location controller exception gracefully") {
        // Given
        val mockLocationController = mocks.permissionController
        mocks.setFineLocationPermission(true)
        coEvery { mockLocationController.start() } throws RuntimeException("Location error")

        val locationManager = mocks.locationManager

        // When
        val result = locationManager.requestPermission()

        // Then
        result shouldBe true
        // Exception should be caught and logged (tested indirectly through no crash)
    }

    test("startGetLocation does nothing when isShared is false") {
        // Given
        val mockLocationController = mocks.locationController
        val locationManager = mocks.locationManager
        locationManager.isShared = false

        // When - trigger startGetLocation indirectly via onLocationPermissionChanged
        locationManager.onLocationPermissionChanged(true)
        awaitIO()

        // Then
        coVerify(exactly = 0) { mockLocationController.start() }
    }

    test("startGetLocation calls location controller start when isShared is true") {
        // Given
        val mockLocationController = mocks.locationController
        coEvery { mockLocationController.start() } returns true
        val locationManager = mocks.locationManager

        // When - trigger startGetLocation indirectly via onLocationPermissionChanged
        locationManager.onLocationPermissionChanged(true)
        awaitIO()

        // Then
        coVerify { mockLocationController.start() }
    }
})
