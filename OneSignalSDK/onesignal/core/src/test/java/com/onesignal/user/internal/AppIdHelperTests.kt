package com.onesignal.user.internal

import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * Unit tests for the resolveAppId function in AppIdResolution.kt
 *
 * These tests focus on the pure business logic of App ID resolution,
 * complementing the integration tests in SDKInitTests.kt which test
 * end-to-end SDK initialization behavior.
 */
class AppIdHelperTests : FunSpec({
    // Test constants - using consistent naming with SDKInitTests
    val testAppId = "appId"
    val differentAppId = "different-app-id"
    val legacyAppId = "legacy-app-id"

    beforeEach {
        Logging.logLevel = LogLevel.NONE
    }

    test("resolveAppId with new appId and no existing appId forces user creation") {
        // Given - fresh config model with no appId property
        val configModel = ConfigModel()
        // Don't set any appId - simulates fresh install

        val mockPreferencesService = mockk<IPreferencesService>(relaxed = true)

        // When
        val result = resolveAppId(testAppId, configModel, mockPreferencesService)

        // Then
        result.appId shouldBe testAppId
        result.forceCreateUser shouldBe true
        result.failed shouldBe false

        // Should not check legacy preferences when appId is provided
        verify(exactly = 0) {
            mockPreferencesService.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID)
        }
    }

    test("resolveAppId with same appId as existing does not force user creation") {
        // Given - config model with existing appId
        val configModel = ConfigModel()
        configModel.appId = differentAppId

        val mockPreferencesService = mockk<IPreferencesService>(relaxed = true)

        // When
        val result = resolveAppId(differentAppId, configModel, mockPreferencesService)

        // Then
        result.appId shouldBe differentAppId
        result.forceCreateUser shouldBe false
        result.failed shouldBe false
    }

    test("resolveAppId with different appId than existing forces user creation") {
        // Given - config model with different existing appId
        val configModel = ConfigModel()
        configModel.appId = differentAppId

        val mockPreferencesService = mockk<IPreferencesService>(relaxed = true)

        // When
        val result = resolveAppId(testAppId, configModel, mockPreferencesService)

        // Then
        result.appId shouldBe testAppId
        result.forceCreateUser shouldBe true
        result.failed shouldBe false
    }

    test("resolveAppId with null appId and existing appId in config returns existing") {
        // Given - config model with existing appId
        val configModel = ConfigModel()
        configModel.appId = differentAppId

        val mockPreferencesService = mockk<IPreferencesService>(relaxed = true)

        // When
        val result = resolveAppId(null, configModel, mockPreferencesService)

        // Then
        result.appId shouldBe null // input was null, so resolved stays null but config has existing
        result.forceCreateUser shouldBe false
        result.failed shouldBe false

        // Should not check legacy preferences when config already has appId
        verify(exactly = 0) {
            mockPreferencesService.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID)
        }
    }

    test("resolveAppId with null appId and no existing appId finds legacy appId") {
        // Given - fresh config model with no appId property
        val configModel = ConfigModel()

        val mockPreferencesService = mockk<IPreferencesService>()
        every {
            mockPreferencesService.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID)
        } returns legacyAppId

        // When
        val result = resolveAppId(null, configModel, mockPreferencesService)

        // Then
        result.appId shouldBe legacyAppId
        result.forceCreateUser shouldBe true // Legacy appId found forces user creation
        result.failed shouldBe false

        // Should check legacy preferences
        verify(exactly = 1) {
            mockPreferencesService.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID)
        }
    }

    test("resolveAppId with null appId and no existing appId and no legacy appId fails") {
        // Given - fresh config model with no appId property and no legacy appId
        val configModel = ConfigModel()

        val mockPreferencesService = mockk<IPreferencesService>()
        every {
            mockPreferencesService.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID)
        } returns null

        // When
        val result = resolveAppId(null, configModel, mockPreferencesService)

        // Then
        result.appId shouldBe null
        result.forceCreateUser shouldBe false
        result.failed shouldBe true

        // Should check legacy preferences
        verify(exactly = 1) {
            mockPreferencesService.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID)
        }
    }

    test("AppIdResolution data class has correct properties") {
        // Given
        val appIdResolution =
            AppIdResolution(
                appId = "test-app-id",
                forceCreateUser = true,
                failed = false,
            )

        // Then
        appIdResolution.appId shouldBe "test-app-id"
        appIdResolution.forceCreateUser shouldBe true
        appIdResolution.failed shouldBe false
    }

    test("AppIdResolution handles null appId correctly") {
        // Given
        val appIdResolution =
            AppIdResolution(
                appId = null,
                forceCreateUser = false,
                failed = true,
            )

        // Then
        appIdResolution.appId shouldBe null
        appIdResolution.forceCreateUser shouldBe false
        appIdResolution.failed shouldBe true
    }

    test("configModel hasProperty check works correctly with appId set") {
        // Given - config model with appId explicitly set
        val configModel = ConfigModel()
        configModel.appId = differentAppId

        val mockPreferencesService = mockk<IPreferencesService>(relaxed = true)

        // When
        val result = resolveAppId(testAppId, configModel, mockPreferencesService)

        // Then - should detect property exists and force user creation due to different appId
        result.appId shouldBe testAppId
        result.forceCreateUser shouldBe true
        result.failed shouldBe false
    }

    test("empty string appId is treated as null") {
        // Given - config model with no appId
        val configModel = ConfigModel()

        val mockPreferencesService = mockk<IPreferencesService>()
        every {
            mockPreferencesService.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID)
        } returns legacyAppId

        // When - pass empty string (which should be treated similar to null in practice)
        val result = resolveAppId("", configModel, mockPreferencesService)

        // Then - empty string is still treated as a valid input appId
        result.appId shouldBe ""
        result.forceCreateUser shouldBe true
        result.failed shouldBe false

        // Should not check legacy preferences when appId is provided (even if empty)
        verify(exactly = 0) {
            mockPreferencesService.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID)
        }
    }

    test("resolveAppId with existing appId property but same value") {
        // Given - config model with the same appId already set
        val configModel = ConfigModel()
        configModel.appId = differentAppId

        val mockPreferencesService = mockk<IPreferencesService>(relaxed = true)

        // When
        val result = resolveAppId(differentAppId, configModel, mockPreferencesService)

        // Then - should not force user creation when appId is unchanged
        result.appId shouldBe differentAppId
        result.forceCreateUser shouldBe false
        result.failed shouldBe false
    }

    test("legacy appId fallback when config model exists but has no appId property") {
        // Given - config model that exists but doesn't have appId set
        val configModel = ConfigModel()
        // Don't set appId to simulate hasProperty returning false

        val mockPreferencesService = mockk<IPreferencesService>()
        every {
            mockPreferencesService.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID)
        } returns legacyAppId

        // When
        val result = resolveAppId(null, configModel, mockPreferencesService)

        // Then
        result.appId shouldBe legacyAppId
        result.forceCreateUser shouldBe true
        result.failed shouldBe false

        verify(exactly = 1) {
            mockPreferencesService.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID)
        }
    }
})
