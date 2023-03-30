package com.onesignal.location.internal.permissions

import com.onesignal.core.internal.permissions.IRequestPermissionService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.AndroidMockHelper
import com.onesignal.notifications.extensions.RobolectricTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.runner.junit4.KotestTestRunner
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.delay
import org.junit.runner.RunWith

@RobolectricTest
@RunWith(KotestTestRunner::class)
class LocationPermissionControllerTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("prompt will return true once permission is accepted by user") {
        /* Given */
        val mockRequestPermissionService = mockk<IRequestPermissionService>()

        val locationPermissionController = LocationPermissionController(
            mockRequestPermissionService,
            AndroidMockHelper.applicationService(),
        )

        every { mockRequestPermissionService.startPrompt(any(), any(), any(), any()) } coAnswers {
            delay(1000)
            locationPermissionController.onAccept()
        }

        /* When */
        val beforeTime = System.currentTimeMillis()
        val response = locationPermissionController.prompt(false, "permission")
        val afterTime = System.currentTimeMillis()

        val deltaTime = afterTime - beforeTime

        /* Then */
        response shouldBe true
        deltaTime shouldBeGreaterThan 1000
        verify(exactly = 1) { mockRequestPermissionService.startPrompt(false, any(), "permission", any()) }
    }

    test("prompt will return false once permission is rejected by user") {
        /* Given */
        val mockRequestPermissionService = mockk<IRequestPermissionService>()

        val locationPermissionController = LocationPermissionController(
            mockRequestPermissionService,
            AndroidMockHelper.applicationService(),
        )

        every { mockRequestPermissionService.startPrompt(any(), any(), any(), any()) } coAnswers {
            delay(1000)
            locationPermissionController.onReject(false)
        }

        /* When */
        val beforeTime = System.currentTimeMillis()
        val response = locationPermissionController.prompt(false, "permission")
        val afterTime = System.currentTimeMillis()

        val deltaTime = afterTime - beforeTime

        /* Then */
        response shouldBe false
        deltaTime shouldBeGreaterThan 1000
        verify(exactly = 1) { mockRequestPermissionService.startPrompt(false, any(), "permission", any()) }
    }

    test("prompt will notify subscribers as accepted once permission is accepted by user") {
        /* Given */
        val mockRequestPermissionService = mockk<IRequestPermissionService>()

        val locationPermissionController = LocationPermissionController(
            mockRequestPermissionService,
            AndroidMockHelper.applicationService(),
        )

        every { mockRequestPermissionService.startPrompt(any(), any(), any(), any()) } coAnswers {
            locationPermissionController.onAccept()
        }
        val spyLocationPermissionChangedHandler = spyk<ILocationPermissionChangedHandler>()

        /* When */
        locationPermissionController.subscribe(spyLocationPermissionChangedHandler)
        locationPermissionController.prompt(false, "permission")

        /* Then */
        verify(exactly = 1) { spyLocationPermissionChangedHandler.onLocationPermissionChanged(true) }
    }

    test("prompt will notify subscribers as rejected once permission is rejected by user") {
        /* Given */
        val mockRequestPermissionService = mockk<IRequestPermissionService>()

        val locationPermissionController = LocationPermissionController(
            mockRequestPermissionService,
            AndroidMockHelper.applicationService(),
        )

        every { mockRequestPermissionService.startPrompt(any(), any(), any(), any()) } coAnswers {
            locationPermissionController.onReject(false)
        }
        val spyLocationPermissionChangedHandler = spyk<ILocationPermissionChangedHandler>()

        /* When */
        locationPermissionController.subscribe(spyLocationPermissionChangedHandler)
        locationPermissionController.prompt(false, "permission")

        /* Then */
        verify(exactly = 1) { spyLocationPermissionChangedHandler.onLocationPermissionChanged(false) }
    }
})
