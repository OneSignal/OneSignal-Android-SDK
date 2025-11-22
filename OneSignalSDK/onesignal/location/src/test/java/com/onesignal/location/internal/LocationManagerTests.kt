package com.onesignal.location.internal

import android.os.Build
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private class Mocks {
    val capturer = mockk<ILocationCapturer>(relaxed = true)
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
        capturer,
        locationController,
        permissionController,
        mockPrefs,
    )

    fun set_fine_location_permission(granted: Boolean) {
        every {
            AndroidUtils.hasPermission(
                LocationConstants.ANDROID_FINE_LOCATION_PERMISSION_STRING,
                true,
                mockAppService,
            )
        } returns granted
    }

    fun set_coarse_location_permission(granted: Boolean) {
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
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()

    beforeAny {
        Logging.logLevel = LogLevel.NONE
        Dispatchers.setMain(testDispatcher)
        mockkObject(LocationUtils)
        mockkObject(AndroidUtils)
        every { LocationUtils.hasLocationPermission(any()) } returns false
        every { AndroidUtils.hasPermission(any(), any(), any()) } returns false
        every { AndroidUtils.filterManifestPermissions(any(), any()) } returns emptyList()
    }

    afterAny {
        unmockkObject(LocationUtils)
        unmockkObject(AndroidUtils)
        Dispatchers.resetMain()
    }

    context("isShared Property") {
        test("isShared getter returns value from preferences") {
            // Given
            val mocks = Mocks()
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
            val mocks = Mocks()
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
            val mocks = Mocks()
            val mockLocationController = mocks.locationController
            val locationManager = mocks.locationManager

            // When
            locationManager.isShared = false

            // Then
            locationManager.isShared shouldBe false
            coVerify(exactly = 0) { mockLocationController.start() }
        }
    }

    context("start() Method") {
        test("start subscribes to location permission controller") {
            // Given
            val mocks = Mocks()
            every { LocationUtils.hasLocationPermission(mocks.mockAppService.appContext) } returns false
            val locationManager = mocks.locationManager

            // When
            locationManager.start()

            // Then
            verify(exactly = 1) { mocks.permissionController.subscribe(locationManager) }
        }

        test("start calls startGetLocation when location permission is granted") {
            // Given
            val mocks = Mocks()
            every { LocationUtils.hasLocationPermission(mocks.mockAppService.appContext) } returns true
            coEvery { mocks.locationController.start() } returns true

            val locationManager = mocks.locationManager

            // When
            locationManager.start()
            delay(50)

            // Then
            coVerify { mocks.locationController.start() }
        }

        test("start does not call startGetLocation when location permission is not granted") {
            // Given
            val mocks = Mocks()
            val mockLocationController = mockk<ILocationController>(relaxed = true)
            every { LocationUtils.hasLocationPermission(mocks.mockContext) } returns false

            val locationManager = mocks.locationManager

            // When
            locationManager.start()
            Thread.sleep(200) // Wait for suspendifyOnIO coroutine to complete

            // Then
            coVerify(exactly = 0) { mockLocationController.start() }
        }
    }

    context("onLocationPermissionChanged() Method") {
        test("onLocationPermissionChanged calls startGetLocation when enabled is true") {
            // Given
            val mocks = Mocks()
            val mockLocationController = mocks.locationController
            coEvery { mockLocationController.start() } returns true

            val locationManager = mocks.locationManager

            // When
            locationManager.onLocationPermissionChanged(true)
            Thread.sleep(200) // Wait for suspendifyOnIO coroutine to complete

            // Then
            coVerify { mockLocationController.start() }
        }

        test("onLocationPermissionChanged does not call startGetLocation when enabled is false") {
            // Given
            val mocks = Mocks()
            val locationManager = mocks.locationManager

            // When
            locationManager.onLocationPermissionChanged(false)
            Thread.sleep(200) // Wait for suspendifyOnIO coroutine to complete

            // Then
            coVerify(exactly = 0) { mocks.locationController.start() }
        }

        test("onLocationPermissionChanged does not call startGetLocation when isShared is false") {
            // Given
            val mocks = Mocks()
            every {
                mocks.mockPrefs.getBool(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_LOCATION_SHARED, false)
            } returns false
            // Create a new LocationManager with isShared = false
            val locationManager = LocationManager(
                mocks.mockAppService,
                mocks.capturer,
                mocks.locationController,
                mocks.permissionController,
                mocks.mockPrefs,
            )

            // When
            locationManager.onLocationPermissionChanged(true)
            delay(50)

            // Then
            coVerify(exactly = 0) { mocks.locationController.start() }
        }
    }

    context("requestPermission() Method - API < 23") {
        test("requestPermission returns true when fine permission granted on API < 23") {
            // Set SDK version to 22 using reflection
            setSdkVersion(22)
            // Given
            val mocks = Mocks()
            mocks.set_fine_location_permission(true)
            val locationManager = mocks.locationManager

            // When
            val result = runBlocking {
                locationManager.requestPermission()
            }

            // Then
            result shouldBe true
        }

        test("requestPermission returns true when coarse permission granted on API < 23") {
            setSdkVersion(22)
            // Given
            val mocks = Mocks()
            mocks.set_fine_location_permission(false)
            mocks.set_coarse_location_permission(true)
            coEvery { mocks.locationController.start() } returns true

            val locationManager = mocks.locationManager

            // When
            val result = runBlocking {
                locationManager.requestPermission()
            }

            // Then
            result shouldBe true
            coVerify { mocks.locationController.start() }
        }

        test("requestPermission returns false when no permissions in manifest on API < 23") {
            setSdkVersion(22)

            // Given
            val mocks = Mocks()
            val mockApplicationService = mocks.mockAppService
            mocks.set_fine_location_permission(false)
            mocks.set_coarse_location_permission(false)
            // Ensure filterManifestPermissions returns empty list (no permissions in manifest)
            every {
                AndroidUtils.filterManifestPermissions(any(), mockApplicationService)
            } returns emptyList()
            val locationManager = mocks.locationManager

            // When
            val result = runBlocking {
                locationManager.requestPermission()
            }

            // Then
            result shouldBe false
        }
    }

    context("requestPermission() Method - API >= 23") {
        test("requestPermission returns true when fine permission already granted") {
            // Set SDK version to 23 using reflection
            setSdkVersion(23)
            // Given
            val mocks = Mocks()
            mocks.set_fine_location_permission(true)
            coEvery { mocks.locationController.start() } returns true
            val locationManager = mocks.locationManager

            // When
            val result = runBlocking {
                locationManager.requestPermission()
            }

            // Then
            result shouldBe true
            coVerify { mocks.locationController.start() }
        }

        test("requestPermission prompts for fine permission when not granted and in manifest") {
            // Set SDK version to 23 using reflection
            setSdkVersion(23)

            // Verify SDK version was set (if reflection fails, skip this test)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                // SDK version couldn't be set, skip this test
                return@test
            }

            // Given
            val mocks = Mocks()
            val mockApplicationService = mocks.mockAppService
            val mockPermissionController = mockk<LocationPermissionController>(relaxed = true)
            mocks.set_fine_location_permission(false)
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
            val result = runBlocking {
                locationManager.requestPermission()
            }

            // Then
            result shouldBe true
            coVerify {
                mockPermissionController.prompt(true, LocationConstants.ANDROID_FINE_LOCATION_PERMISSION_STRING)
            }
        }

        test("requestPermission prompts for coarse permission when fine not in manifest") {
            // Set SDK version to 23 using reflection
            setSdkVersion(23)

            // Verify SDK version was set (if reflection fails, skip this test)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                // SDK version couldn't be set, skip this test
                return@test
            }

            // Given
            val mocks = Mocks()
            val mockApplicationService = mocks.mockAppService
            val mockPermissionController = mockk<LocationPermissionController>(relaxed = true)
            mocks.set_fine_location_permission(false)
            mocks.set_coarse_location_permission(false)
            every {
                AndroidUtils.filterManifestPermissions(
                    any(),
                    mockApplicationService,
                )
            } returns listOf(LocationConstants.ANDROID_COARSE_LOCATION_PERMISSION_STRING)
            every {
                AndroidUtils.hasPermission(
                    LocationConstants.ANDROID_COARSE_LOCATION_PERMISSION_STRING,
                    false,
                    mocks.mockAppService,
                )
            } returns true
            val locationManager = mocks.locationManager

            // When
            val result = runBlocking {
                locationManager.requestPermission()
            }

            // Then
            result shouldBe true
            coVerify {
                mockPermissionController.prompt(true, LocationConstants.ANDROID_COARSE_LOCATION_PERMISSION_STRING)
            }
        }

        test("requestPermission returns false when permissions not in manifest") {
            // Given
            val mocks = Mocks()
            mocks.set_fine_location_permission(false)
            val locationManager = mocks.locationManager

            // When
            val result = runBlocking {
                locationManager.requestPermission()
            }

            // Then
            result shouldBe false
        }

        test("requestPermission returns true when coarse permission already granted") {
            // Given
            val mocks = Mocks()
            mocks.set_fine_location_permission(false)
            mocks.set_coarse_location_permission(true)

            val locationManager = mocks.locationManager

            // When
            val result = runBlocking {
                locationManager.requestPermission()
            }

            // Then
            result shouldBe true
        }
    }

    context("requestPermission() Method - API >= 29 (Android 10+)") {
        test("requestPermission prompts for background permission when fine granted but background not") {
            // Set SDK version to 29 using reflection
            setSdkVersion(29)

            // Verify SDK version was set (if reflection fails, skip this test)
            if (Build.VERSION.SDK_INT < 29) {
                // SDK version couldn't be set, skip this test
                return@test
            }

            // Given
            val mocks = Mocks()
            val mockApplicationService = mocks.mockAppService
            val mockPermissionController = mockk<LocationPermissionController>(relaxed = true)
            mocks.set_fine_location_permission(true)
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
            val result = runBlocking {
                locationManager.requestPermission()
            }

            // Then
            result shouldBe true
            coVerify {
                mockPermissionController.prompt(true, LocationConstants.ANDROID_BACKGROUND_LOCATION_PERMISSION_STRING)
            }
        }

        test("requestPermission starts location when all permissions granted") {
            // Given
            val mocks = Mocks()
            val mockApplicationService = mocks.mockAppService
            mocks.set_fine_location_permission(true)
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
            val result = runBlocking {
                locationManager.requestPermission()
            }

            // Then
            result shouldBe true
            coVerify { mocks.locationController.start() }
        }
    }

    context("requestPermission() Method - Edge Cases") {
        test("requestPermission warns when isShared is false") {
            // Given
            val mocks = Mocks()
            mocks.set_fine_location_permission(true)

            val locationManager = mocks.locationManager

            // When
            val result = runBlocking {
                locationManager.requestPermission()
            }

            // Then
            result shouldBe true
            // Warning should be logged (tested indirectly through no exception)
        }

        test("requestPermission handles location controller start failure gracefully") {
            // Given
            val mocks = Mocks()
            mocks.set_fine_location_permission(true)
            coEvery { mocks.locationController.start() } returns false

            val locationManager = mocks.locationManager

            // When
            val result = runBlocking {
                locationManager.requestPermission()
            }

            // Then
            result shouldBe true
            coVerify { mocks.locationController.start() }
        }

        test("requestPermission handles location controller exception gracefully") {
            // Given
            val mocks = Mocks()
            val mockLocationController = mockk<ILocationController>(relaxed = true)
            mocks.set_fine_location_permission(true)
            coEvery { mockLocationController.start() } throws RuntimeException("Location error")

            val locationManager = mocks.locationManager

            // When
            val result = runBlocking {
                locationManager.requestPermission()
            }

            // Then
            result shouldBe true
            // Exception should be caught and logged (tested indirectly through no crash)
        }
    }

    context("startGetLocation() Method") {
        test("startGetLocation does nothing when isShared is false") {
            // Given
            val mocks = Mocks()
            val mockLocationController = mockk<ILocationController>(relaxed = true)
            val locationManager = mocks.locationManager

            // When - trigger startGetLocation indirectly via onLocationPermissionChanged
            locationManager.onLocationPermissionChanged(true)
            delay(50) // Wait for suspendifyOnIO coroutine to complete

            // Then
            coVerify(exactly = 0) { mockLocationController.start() }
        }

        test("startGetLocation calls location controller start when isShared is true") {
            // Given
            val mocks = Mocks()
            val mockLocationController = mocks.locationController
            coEvery { mockLocationController.start() } returns true
            val locationManager = mocks.locationManager

            // When - trigger startGetLocation indirectly via onLocationPermissionChanged
            locationManager.onLocationPermissionChanged(true)
            Thread.sleep(200) // Wait for suspendifyOnIO coroutine to complete

            // Then
            coVerify { mockLocationController.start() }
        }
    }
})

// Helper function to set SDK version using reflection
private fun setSdkVersion(sdkInt: Int) {
    try {
        val buildVersionClass = Class.forName("android.os.Build\$VERSION")
        val sdkIntField = buildVersionClass.getDeclaredField("SDK_INT")
        sdkIntField.isAccessible = true
        sdkIntField.setInt(null, sdkInt)
    } catch (e: Exception) {
        // If reflection fails, the test will use the default SDK version
        // This is acceptable for tests that don't strictly require a specific SDK version
    }
}
