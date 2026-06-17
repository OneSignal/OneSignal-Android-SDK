package com.onesignal.location.internal.controller

import android.location.Location
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.location.internal.controller.impl.GmsLocationController
import com.onesignal.location.mocks.FusedLocationApiWrapperMock
import com.onesignal.location.shadows.ShadowGoogleApiClient
import com.onesignal.location.shadows.ShadowGoogleApiClientBuilder
import com.onesignal.mocks.AndroidMockHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifySequence
import org.robolectric.annotation.Config

@Config(
    packageName = "com.onesignal.example",
    shadows = [ShadowGoogleApiClientBuilder::class],
    sdk = [26],
)
@RobolectricTest
class GmsLocationControllerTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("start will connect and fire locationChanged with location") {
        // Given
        val location = Location("TEST_PROVIDER")
        location.latitude = 123.45
        location.longitude = 678.91
        val fusedLocationApiWrapperMock = FusedLocationApiWrapperMock(listOf(location))

        val applicationService = AndroidMockHelper.applicationService()
        every { applicationService.isInForeground } returns true
        val gmsLocationController = GmsLocationController(applicationService, fusedLocationApiWrapperMock)

        val spyLocationUpdateHandler = spyk<ILocationUpdatedHandler>()
        gmsLocationController.subscribe(spyLocationUpdateHandler)

        // When
        val response = gmsLocationController.start()

        // Then
        response shouldBe true
        ShadowGoogleApiClient.connected shouldBe true
        verify(exactly = 1) {
            spyLocationUpdateHandler.onLocationChanged(
                withArg {
                    it.latitude shouldBe 123.45
                    it.longitude shouldBe 678.91
                },
            )
        }
    }

    test("start twice will return the initial location") {
        // Given
        val location1 = Location("TEST_PROVIDER")
        location1.latitude = 123.45
        location1.longitude = 678.91

        val location2 = Location("TEST_PROVIDER")
        location2.latitude = 678.91
        location2.longitude = 123.45
        val fusedLocationApiWrapperMock = FusedLocationApiWrapperMock(listOf(location1, location2))

        val applicationService = AndroidMockHelper.applicationService()
        every { applicationService.isInForeground } returns true
        val gmsLocationController = GmsLocationController(applicationService, fusedLocationApiWrapperMock)

        val spyLocationUpdateHandler = spyk<ILocationUpdatedHandler>()
        gmsLocationController.subscribe(spyLocationUpdateHandler)

        // When
        val response1 = gmsLocationController.start()
        val response2 = gmsLocationController.start()

        // Then
        response1 shouldBe true
        response2 shouldBe true
        ShadowGoogleApiClient.connected shouldBe true
        verifySequence {
            spyLocationUpdateHandler.onLocationChanged(
                withArg {
                    it.latitude shouldBe 123.45
                    it.longitude shouldBe 678.91
                },
            )
            spyLocationUpdateHandler.onLocationChanged(
                withArg {
                    it.latitude shouldBe 123.45
                    it.longitude shouldBe 678.91
                },
            )
        }
    }

    test("getLastLocation will retrieve a new location") {
        // Given
        val location1 = Location("TEST_PROVIDER")
        location1.latitude = 123.45
        location1.longitude = 678.91

        val location2 = Location("TEST_PROVIDER")
        location2.latitude = 678.91
        location2.longitude = 123.45

        val fusedLocationApiWrapperMock = FusedLocationApiWrapperMock(listOf(location1, location2))
        val applicationService = AndroidMockHelper.applicationService()
        every { applicationService.isInForeground } returns true
        val gmsLocationController = GmsLocationController(applicationService, fusedLocationApiWrapperMock)

        val spyLocationUpdateHandler = spyk<ILocationUpdatedHandler>()
        gmsLocationController.subscribe(spyLocationUpdateHandler)

        // When
        val response = gmsLocationController.start()
        val lastLocation = gmsLocationController.getLastLocation()

        // Then
        response shouldBe true
        lastLocation shouldNotBe null
        lastLocation!!.latitude shouldBe 678.91
        lastLocation.longitude shouldBe 123.45
        ShadowGoogleApiClient.connected shouldBe true
        verifySequence {
            spyLocationUpdateHandler.onLocationChanged(
                withArg {
                    it.latitude shouldBe 123.45
                    it.longitude shouldBe 678.91
                },
            )
        }
    }

    test("stop will disconnect") {
        // Given
        val location1 = Location("TEST_PROVIDER")
        location1.latitude = 123.45
        location1.longitude = 678.91

        val location2 = Location("TEST_PROVIDER")
        location2.latitude = 678.91
        location2.longitude = 123.45

        val fusedLocationApiWrapperMock = FusedLocationApiWrapperMock(listOf(location1, location2))
        val applicationService = AndroidMockHelper.applicationService()
        every { applicationService.isInForeground } returns true
        val gmsLocationController = GmsLocationController(applicationService, fusedLocationApiWrapperMock)

        val spyLocationUpdateHandler = spyk<ILocationUpdatedHandler>()
        gmsLocationController.subscribe(spyLocationUpdateHandler)

        // When
        val response = gmsLocationController.start()
        gmsLocationController.stop()

        // Then
        response shouldBe true
        ShadowGoogleApiClient.connected shouldBe false
        verifySequence {
            spyLocationUpdateHandler.onLocationChanged(
                withArg {
                    it.latitude shouldBe 123.45
                    it.longitude shouldBe 678.91
                },
            )
        }
    }

    // Regression: when isShared is enabled before location permission is granted, the
    // GoogleApiClient connects (which does not require permission) but the first
    // requestLocationUpdates fails. A later start() (e.g. triggered by the permission grant)
    // previously short-circuited and only fired a one-shot last location, leaving the live
    // subscription dead until the next app focus change. It must now re-arm on that start().
    test("start re-arms the live location subscription on a subsequent start") {
        // Given
        val location = Location("TEST_PROVIDER")
        location.latitude = 123.45
        location.longitude = 678.91
        val fusedLocationApiWrapperMock = FusedLocationApiWrapperMock(listOf(location))

        val applicationService = AndroidMockHelper.applicationService()
        every { applicationService.isInForeground } returns true
        val gmsLocationController = GmsLocationController(applicationService, fusedLocationApiWrapperMock)

        // When
        val response1 = gmsLocationController.start()
        val response2 = gmsLocationController.start()

        // Then
        response1 shouldBe true
        response2 shouldBe true
        // Once on the initial init, and again when the second start re-arms the subscription.
        fusedLocationApiWrapperMock.requestLocationUpdatesCallCount shouldBe 2
    }

    // Regression: a requestLocationUpdates that fails (e.g. a swallowed SecurityException
    // when permission is missing) must not be recorded as an active request, otherwise
    // close()/refresh would try to cancel a subscription that never existed.
    test("a failed requestLocationUpdates is not treated as an active subscription") {
        // Given
        val location = Location("TEST_PROVIDER")
        location.latitude = 123.45
        location.longitude = 678.91
        val fusedLocationApiWrapperMock = FusedLocationApiWrapperMock(listOf(location))
        fusedLocationApiWrapperMock.requestLocationUpdatesResult = false

        val applicationService = AndroidMockHelper.applicationService()
        every { applicationService.isInForeground } returns true
        val gmsLocationController = GmsLocationController(applicationService, fusedLocationApiWrapperMock)

        // When
        gmsLocationController.start()
        gmsLocationController.stop()

        // Then
        fusedLocationApiWrapperMock.requestLocationUpdatesCallCount shouldBe 1
        fusedLocationApiWrapperMock.cancelLocationUpdatesCallCount shouldBe 0
    }
})
