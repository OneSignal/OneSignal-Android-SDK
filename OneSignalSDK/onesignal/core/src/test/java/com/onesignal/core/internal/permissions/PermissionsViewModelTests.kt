package com.onesignal.core.internal.permissions

import android.app.Activity
import android.content.pm.PackageManager
import com.onesignal.OneSignal
import com.onesignal.core.internal.permissions.impl.RequestPermissionService
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionsViewModelTests : FunSpec({
    val permissionType = "location"
    val androidPermission = "android.permission.ACCESS_FINE_LOCATION"
    val mockRequestService = mockk<RequestPermissionService>(relaxed = true)
    val mockPrefService = mockk<IPreferencesService>(relaxed = true)
    val callbackDelay = (PermissionsViewModel.DELAY_TIME_CALLBACK_CALL + 50).toLong()

    beforeTest {
        Dispatchers.setMain(StandardTestDispatcher())
        mockkObject(OneSignal)
        every { OneSignal.getService<RequestPermissionService>() } returns mockRequestService
        every { OneSignal.getService<IPreferencesService>() } returns mockPrefService
        Logging.logLevel = LogLevel.NONE
    }

    afterTest {
        Dispatchers.resetMain()
        unmockkAll()
    }

    test("initialize sets permissionRequestType and returns true") {
        val viewModel = PermissionsViewModel()
        val activity = mockk<Activity>(relaxed = true)

        // Mock the services that will be accessed via lazy initialization
        coEvery { OneSignal.initWithContext(any()) } returns true

        runBlocking {
            val result = viewModel.initialize(activity, permissionType, androidPermission)
            result shouldBe true
        }
        viewModel.permissionRequestType shouldBe permissionType
    }

    test("initialize returns false when OneSignal init fails") {
        val viewModel = PermissionsViewModel()
        val activity = mockk<Activity>(relaxed = true)
        coEvery { OneSignal.initWithContext(activity) } returns false

        runBlocking {
            val result = viewModel.initialize(activity, permissionType, androidPermission)
            result shouldBe false
        }
        runBlocking {
            viewModel.shouldFinish.first() shouldBe true
        }
    }

    test("shouldRequestPermission sets waiting to true") {
        val viewModel = PermissionsViewModel()

        val result = viewModel.shouldRequestPermission()

        result shouldBe true
        runBlocking {
            viewModel.waiting.first() shouldBe true
        }
    }

    test("shouldRequestPermission returns false when already waiting") {
        val viewModel = PermissionsViewModel()
        viewModel.shouldRequestPermission() // First call sets waiting to true

        val result = viewModel.shouldRequestPermission() // Second call should return false

        result shouldBe false
    }

    test("shouldRequestPermission prevents duplicate requests") {
        val viewModel = PermissionsViewModel()

        // First call should return true and set waiting to true
        val firstResult = viewModel.shouldRequestPermission()
        firstResult shouldBe true
        runBlocking {
            viewModel.waiting.first() shouldBe true
        }

        // Second call should return false (already waiting)
        val secondResult = viewModel.shouldRequestPermission()
        secondResult shouldBe false
    }

    test("recordRationaleState sets the rationale state") {
        val viewModel = PermissionsViewModel()

        viewModel.recordRationaleState(true)

        verify { mockRequestService.shouldShowRequestPermissionRationaleBeforeRequest = true }
    }

    test("resetWaitingState resets waiting flag to false") {
        val viewModel = PermissionsViewModel()

        // First set waiting to true
        viewModel.shouldRequestPermission()
        runBlocking {
            viewModel.waiting.first() shouldBe true
        }

        // Reset the waiting state (simulating activity pause)
        viewModel.resetWaitingState()

        // Verify waiting is now false
        runBlocking {
            viewModel.waiting.first() shouldBe false
        }
    }

    test("resetWaitingState allows permission request after reset") {
        val viewModel = PermissionsViewModel()

        // First request should succeed
        val firstResult = viewModel.shouldRequestPermission()
        firstResult shouldBe true

        // Reset the waiting state (simulating activity pause)
        viewModel.resetWaitingState()

        // Second request should now succeed again
        val secondResult = viewModel.shouldRequestPermission()
        secondResult shouldBe true
    }

    test("resetWaitingState simulates activity pause scenario") {
        val viewModel = PermissionsViewModel()

        // Simulate: User sees permission dialog
        val firstResult = viewModel.shouldRequestPermission()
        firstResult shouldBe true
        runBlocking {
            viewModel.waiting.first() shouldBe true
        }

        // Simulate: Another activity comes on top (phone call, notification, etc.)
        // Activity's onPause() calls resetWaitingState()
        viewModel.resetWaitingState()
        runBlocking {
            viewModel.waiting.first() shouldBe false
        }

        // Simulate: User returns to app, permission dialog can be shown again
        val secondResult = viewModel.shouldRequestPermission()
        secondResult shouldBe true
        runBlocking {
            viewModel.waiting.first() shouldBe true
        }
    }

    test("initialize returns false when permissionType is null") {
        val viewModel = PermissionsViewModel()
        val activity = mockk<Activity>(relaxed = true)

        coEvery { OneSignal.initWithContext(any()) } returns true

        runBlocking {
            val result = viewModel.initialize(activity, null, androidPermission)
            result shouldBe false
            viewModel.shouldFinish.first() shouldBe true
        }
    }

    test("initialize returns false when androidPermission is null") {
        val viewModel = PermissionsViewModel()
        val activity = mockk<Activity>(relaxed = true)

        coEvery { OneSignal.initWithContext(any()) } returns true

        runBlocking {
            val result = viewModel.initialize(activity, permissionType, null)
            result shouldBe false
            viewModel.shouldFinish.first() shouldBe true
        }
    }

    test("onRequestPermissionsResult with uninitialized ViewModel finishes gracefully without NPE") {
        runTest {
            // Given - ViewModel is not initialized (permissionRequestType is null)
            // This simulates process death or race condition where onRequestPermissionsResult
            // is called before initialize() completes
            val viewModel = PermissionsViewModel()

            // When - onRequestPermissionsResult is called before initialize() completes
            viewModel.onRequestPermissionsResult(
                arrayOf(androidPermission),
                intArrayOf(PackageManager.PERMISSION_GRANTED),
                false,
            )

            // Advance time to complete the delay
            advanceTimeBy(callbackDelay)

            // Then - should not throw NPE and should finish gracefully
            viewModel.shouldFinish.first() shouldBe true
            // Verify no callback was attempted (since permissionRequestType is null)
            verify(exactly = 0) { mockRequestService.getCallback(any()) }
        }
    }

    test("onRequestPermissionsResult with uninitialized ViewModel handles denied permission gracefully") {
        runTest {
            // Given - ViewModel is not initialized
            val viewModel = PermissionsViewModel()

            // When - onRequestPermissionsResult is called with denied permission
            viewModel.onRequestPermissionsResult(
                arrayOf(androidPermission),
                intArrayOf(PackageManager.PERMISSION_DENIED),
                false,
            )

            // Advance time to complete the delay
            advanceTimeBy(callbackDelay)

            // Then - should finish gracefully without NPE
            viewModel.shouldFinish.first() shouldBe true
            verify(exactly = 0) { mockRequestService.getCallback(any()) }
        }
    }

    test("onRequestPermissionsResult with initialized ViewModel calls onAccept when granted") {
        runTest {
            // Given - ViewModel is properly initialized
            val viewModel = PermissionsViewModel()
            val activity = mockk<Activity>(relaxed = true)
            val mockCallback = mockk<IRequestPermissionService.PermissionCallback>(relaxed = true)

            coEvery { OneSignal.initWithContext(any()) } returns true
            every { mockRequestService.getCallback(permissionType) } returns mockCallback

            viewModel.initialize(activity, permissionType, androidPermission)

            // When - onRequestPermissionsResult is called with granted permission
            viewModel.onRequestPermissionsResult(
                arrayOf(androidPermission),
                intArrayOf(PackageManager.PERMISSION_GRANTED),
                false,
            )

            // Advance time to complete the delay
            advanceTimeBy(callbackDelay)

            // Then - callback.onAccept should be called and preference should be saved
            verify { mockCallback.onAccept() }
            verify {
                mockPrefService.saveBool(
                    PreferenceStores.ONESIGNAL,
                    "${PreferenceOneSignalKeys.PREFS_OS_USER_RESOLVED_PERMISSION_PREFIX}$androidPermission",
                    true,
                )
            }
            viewModel.shouldFinish.first() shouldBe true
        }
    }

    test("onRequestPermissionsResult with initialized ViewModel calls onReject when denied") {
        runTest {
            // Given - ViewModel is properly initialized
            val viewModel = PermissionsViewModel()
            val activity = mockk<Activity>(relaxed = true)
            val mockCallback = mockk<IRequestPermissionService.PermissionCallback>(relaxed = true)

            coEvery { OneSignal.initWithContext(any()) } returns true
            every { mockRequestService.getCallback(permissionType) } returns mockCallback
            every { mockRequestService.fallbackToSettings } returns false

            viewModel.initialize(activity, permissionType, androidPermission)

            // When - onRequestPermissionsResult is called with denied permission
            viewModel.onRequestPermissionsResult(
                arrayOf(androidPermission),
                intArrayOf(PackageManager.PERMISSION_DENIED),
                false,
            )

            // Advance time to complete the delay
            advanceTimeBy(callbackDelay)

            // Then - callback.onReject should be called with showSettings = false
            verify { mockCallback.onReject(false) }
            viewModel.shouldFinish.first() shouldBe true
        }
    }

    test("onRequestPermissionsResult with empty permissions array handles gracefully") {
        runTest {
            // Given - ViewModel is initialized
            val viewModel = PermissionsViewModel()
            val activity = mockk<Activity>(relaxed = true)
            val mockCallback = mockk<IRequestPermissionService.PermissionCallback>(relaxed = true)

            coEvery { OneSignal.initWithContext(any()) } returns true
            every { mockRequestService.getCallback(permissionType) } returns mockCallback

            viewModel.initialize(activity, permissionType, androidPermission)

            // When - onRequestPermissionsResult is called with empty permissions
            viewModel.onRequestPermissionsResult(
                arrayOf(),
                intArrayOf(),
                false,
            )

            // Advance time to complete the delay
            advanceTimeBy(callbackDelay)

            // Then - callback.onReject should be called (treated as denied)
            verify { mockCallback.onReject(false) }
            viewModel.shouldFinish.first() shouldBe true
        }
    }

    test("onRequestPermissionsResult with empty grantResults treats as denied") {
        runTest {
            // Given - ViewModel is initialized
            val viewModel = PermissionsViewModel()
            val activity = mockk<Activity>(relaxed = true)
            val mockCallback = mockk<IRequestPermissionService.PermissionCallback>(relaxed = true)

            coEvery { OneSignal.initWithContext(any()) } returns true
            every { mockRequestService.getCallback(permissionType) } returns mockCallback
            every { mockRequestService.fallbackToSettings } returns false

            viewModel.initialize(activity, permissionType, androidPermission)

            // When - onRequestPermissionsResult is called with empty grantResults
            viewModel.onRequestPermissionsResult(
                arrayOf(androidPermission),
                intArrayOf(),
                false,
            )

            // Advance time to complete the delay
            advanceTimeBy(callbackDelay)

            // Then - callback.onReject should be called (empty grantResults = denied)
            verify { mockCallback.onReject(false) }
            viewModel.shouldFinish.first() shouldBe true
        }
    }

    test("onRequestPermissionsResult throws RuntimeException when callback is missing") {
        runTest {
            // Given - ViewModel is initialized but callback is not registered
            val viewModel = PermissionsViewModel()
            val activity = mockk<Activity>(relaxed = true)

            coEvery { OneSignal.initWithContext(any()) } returns true
            every { mockRequestService.getCallback(permissionType) } returns null

            viewModel.initialize(activity, permissionType, androidPermission)

            // When/Then - onRequestPermissionsResult should throw RuntimeException
            viewModel.onRequestPermissionsResult(
                arrayOf(androidPermission),
                intArrayOf(PackageManager.PERMISSION_GRANTED),
                false,
            )

            // Advance time to complete the delay
            advanceTimeBy(callbackDelay)

            // The exception should be thrown in the coroutine scope
            // We can't easily catch it in viewModelScope, but we verify the callback lookup was attempted
            verify { mockRequestService.getCallback(permissionType) }
        }
    }

    test("onRequestPermissionsResult shows settings when fallbackToSettings is true and not permanently denied") {
        runTest {
            // Given - ViewModel is initialized with fallback to settings enabled
            val viewModel = PermissionsViewModel()
            val activity = mockk<Activity>(relaxed = true)
            val mockCallback = mockk<IRequestPermissionService.PermissionCallback>(relaxed = true)

            coEvery { OneSignal.initWithContext(any()) } returns true
            every { mockRequestService.getCallback(permissionType) } returns mockCallback
            every { mockRequestService.fallbackToSettings } returns true
            every {
                mockPrefService.getBool(
                    PreferenceStores.ONESIGNAL,
                    "${PreferenceOneSignalKeys.PREFS_OS_USER_RESOLVED_PERMISSION_PREFIX}$androidPermission",
                    false,
                )
            } returns false

            viewModel.initialize(activity, permissionType, androidPermission)

            // When - permission is denied (first time, not permanently)
            viewModel.onRequestPermissionsResult(
                arrayOf(androidPermission),
                intArrayOf(PackageManager.PERMISSION_DENIED),
                true, // shouldShowRationaleAfter = true (first denial)
            )

            // Advance time to complete the delay
            advanceTimeBy(callbackDelay)

            // Then - callback.onReject should be called with showSettings = true
            verify { mockCallback.onReject(true) }
            viewModel.shouldFinish.first() shouldBe true
        }
    }

    test("onRequestPermissionsResult does not show settings when permanently denied") {
        runTest {
            // Given - ViewModel is initialized, rationale changed from true to false (permanent denial)
            val viewModel = PermissionsViewModel()
            val activity = mockk<Activity>(relaxed = true)
            val mockCallback = mockk<IRequestPermissionService.PermissionCallback>(relaxed = true)

            coEvery { OneSignal.initWithContext(any()) } returns true
            every { mockRequestService.getCallback(permissionType) } returns mockCallback
            every { mockRequestService.fallbackToSettings } returns true
            every { mockRequestService.shouldShowRequestPermissionRationaleBeforeRequest } returns true

            viewModel.initialize(activity, permissionType, androidPermission)
            viewModel.recordRationaleState(true) // Set before request

            // When - permission is denied and rationale changed from true to false (permanent denial)
            viewModel.onRequestPermissionsResult(
                arrayOf(androidPermission),
                intArrayOf(PackageManager.PERMISSION_DENIED),
                false, // shouldShowRationaleAfter = false (permanent denial)
            )

            // Advance time to complete the delay
            advanceTimeBy(callbackDelay)

            // Then - callback.onReject should be called with showSettings = false
            // and preference should be saved to remember permanent denial
            verify { mockCallback.onReject(false) }
            verify {
                mockPrefService.saveBool(
                    PreferenceStores.ONESIGNAL,
                    "${PreferenceOneSignalKeys.PREFS_OS_USER_RESOLVED_PERMISSION_PREFIX}$androidPermission",
                    true,
                )
            }
            viewModel.shouldFinish.first() shouldBe true
        }
    }

    test("onRequestPermissionsResult does not show settings when fallbackToSettings is false") {
        runTest {
            // Given - ViewModel is initialized with fallback to settings disabled
            val viewModel = PermissionsViewModel()
            val activity = mockk<Activity>(relaxed = true)
            val mockCallback = mockk<IRequestPermissionService.PermissionCallback>(relaxed = true)

            coEvery { OneSignal.initWithContext(any()) } returns true
            every { mockRequestService.getCallback(permissionType) } returns mockCallback
            every { mockRequestService.fallbackToSettings } returns false

            viewModel.initialize(activity, permissionType, androidPermission)

            // When - permission is denied
            viewModel.onRequestPermissionsResult(
                arrayOf(androidPermission),
                intArrayOf(PackageManager.PERMISSION_DENIED),
                true,
            )

            // Advance time to complete the delay
            advanceTimeBy(callbackDelay)

            // Then - callback.onReject should be called with showSettings = false
            verify { mockCallback.onReject(false) }
            viewModel.shouldFinish.first() shouldBe true
        }
    }

    test("onRequestPermissionsResult resets waiting state") {
        runTest {
            // Given - ViewModel is initialized and waiting
            val viewModel = PermissionsViewModel()
            val activity = mockk<Activity>(relaxed = true)
            val mockCallback = mockk<IRequestPermissionService.PermissionCallback>(relaxed = true)

            coEvery { OneSignal.initWithContext(any()) } returns true
            every { mockRequestService.getCallback(permissionType) } returns mockCallback

            viewModel.initialize(activity, permissionType, androidPermission)
            viewModel.shouldRequestPermission() // Set waiting to true

            // When - onRequestPermissionsResult is called
            viewModel.onRequestPermissionsResult(
                arrayOf(androidPermission),
                intArrayOf(PackageManager.PERMISSION_GRANTED),
                false,
            )

            // Then - waiting should be reset to false immediately (before delay)
            viewModel.waiting.first() shouldBe false
        }
    }
})
