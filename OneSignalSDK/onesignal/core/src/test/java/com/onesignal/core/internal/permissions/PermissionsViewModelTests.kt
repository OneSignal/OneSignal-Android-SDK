package com.onesignal.core.internal.permissions

import android.app.Activity
import com.onesignal.OneSignal
import com.onesignal.core.internal.permissions.impl.RequestPermissionService
import com.onesignal.core.internal.preferences.IPreferencesService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class PermissionsViewModelTests : FunSpec({
    val permissionType = "location"
    val androidPermission = "android.permission.ACCESS_FINE_LOCATION"
    val mockRequestService = mockk<RequestPermissionService>(relaxed = true)
    val mockPrefService = mockk<IPreferencesService>(relaxed = true)

    beforeTest {
        mockkObject(OneSignal)
    }

    afterTest {
        unmockkAll()
    }

    test("initialize sets permissionRequestType and returns true") {
        val viewModel = PermissionsViewModel()
        val activity = mockk<Activity>(relaxed = true)

        // Mock the services that will be accessed via lazy initialization
        coEvery { OneSignal.initWithContext(any()) } returns true
        every { OneSignal.getService<RequestPermissionService>() } returns mockRequestService
        every { OneSignal.getService<IPreferencesService>() } returns mockPrefService

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

        // Mock the service
        every { OneSignal.getService<RequestPermissionService>() } returns mockRequestService

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
})
