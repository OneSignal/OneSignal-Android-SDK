package com.onesignal.core.internal.permissions

import android.app.Activity
import android.content.pm.PackageManager
import com.onesignal.OneSignal
import com.onesignal.core.internal.permissions.impl.RequestPermissionService
import com.onesignal.core.internal.preferences.IPreferencesService
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

    test("onRequestPermissionsResult with uninitialized ViewModel finishes gracefully") {
        runTest {
            // Given - ViewModel is not initialized (permissionRequestType is null)
            val viewModel = PermissionsViewModel()

            // When - onRequestPermissionsResult is called before initialize() completes (race condition)
            viewModel.onRequestPermissionsResult(
                arrayOf(androidPermission),
                intArrayOf(PackageManager.PERMISSION_GRANTED),
                false,
            )

            // Advance time to complete the delay
            advanceTimeBy(callbackDelay) // DELAY_TIME_CALLBACK_CALL (500ms) + buffer

            // Then - should not throw exception
        }
    }

    test("onRequestPermissionsResult with initialized ViewModel calls callback") {
        runTest {
            // Given - ViewModel is properly initialized
            val viewModel = PermissionsViewModel()
            val activity = mockk<Activity>(relaxed = true)
            val mockCallback = mockk<IRequestPermissionService.PermissionCallback>(relaxed = true)

            coEvery { OneSignal.initWithContext(any()) } returns true
            every { mockRequestService.getCallback(permissionType) } returns mockCallback

            viewModel.initialize(activity, permissionType, androidPermission)

            // When - onRequestPermissionsResult is called
            viewModel.onRequestPermissionsResult(
                arrayOf(androidPermission),
                intArrayOf(PackageManager.PERMISSION_GRANTED),
                false,
            )

            // Advance time to complete the delay
            advanceTimeBy(callbackDelay)

            // Then - callback should be called
            verify { mockCallback.onAccept() }
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

            // Then - callback.onReject should be called
            verify { mockCallback.onReject(false) }
            viewModel.shouldFinish.first() shouldBe true
        }
    }
})
