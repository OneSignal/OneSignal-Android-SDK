package com.onesignal.core.internal.device

import com.onesignal.core.internal.device.impl.InstallIdService
import com.onesignal.mocks.MockPreferencesService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class InstallIdServiceTests : FunSpec({
    test("2 calls result in the same value") {
        // Given
        val service = InstallIdService(MockPreferencesService())

        // When
        val value1 = service.getId()
        val value2 = service.getId()

        // Then
        value1 shouldBe value2
    }

    // Real world scenario we are testing is if we cold restart the app we get
    // the same value
    test("reads from shared prefs") {
        // Given
        val sharedPrefs = MockPreferencesService()

        // When
        val service1 = InstallIdService(sharedPrefs)
        val value1 = service1.getId()
        val service2 = InstallIdService(sharedPrefs)
        val value2 = service2.getId()

        // Then
        value1 shouldBe value2
    }
})
