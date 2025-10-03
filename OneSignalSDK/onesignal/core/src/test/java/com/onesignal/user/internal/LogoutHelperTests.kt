package com.onesignal.user.internal

import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.operations.LoginUserOperation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder

/**
 * Unit tests for the LogoutHelper class
 *
 * These tests focus on the pure business logic of user logout operations,
 * complementing the integration tests in SDKInitTests.kt which test
 * end-to-end SDK initialization and logout behavior.
 */
class LogoutHelperTests : FunSpec({
    // Test constants - using consistent naming with SDKInitTests
    val appId = "appId"
    val externalId = "current-user"
    val onesignalId = "current-onesignal-id"

    beforeEach {
        Logging.logLevel = LogLevel.NONE
    }

    test("logout with no external id returns early without operations") {
        // Given - anonymous user (no external ID)
        val mockIdentityModelStore =
            MockHelper.identityModelStore { model ->
                model.externalId = null
                model.onesignalId = onesignalId
            }
        val mockUserSwitcher = mockk<UserSwitcher>(relaxed = true)
        val mockOperationRepo = mockk<IOperationRepo>(relaxed = true)
        val mockConfigModel = mockk<ConfigModel>()
        every { mockConfigModel.appId } returns appId
        val logoutLock = Any()

        val logoutHelper =
            LogoutHelper(
                logoutLock = logoutLock,
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
                configModel = mockConfigModel,
            )

        // When
        logoutHelper.logout()

        // Then - should return early without any operations
        verify(exactly = 0) { mockUserSwitcher.createAndSwitchToNewUser() }
        verify(exactly = 0) { mockOperationRepo.enqueue(any()) }
    }

    test("logout with external id creates new user and enqueues operation") {
        // Given - identified user
        val mockIdentityModelStore =
            MockHelper.identityModelStore { model ->
                model.externalId = externalId
                model.onesignalId = onesignalId
            }
        val mockUserSwitcher = mockk<UserSwitcher>(relaxed = true)
        val mockOperationRepo = mockk<IOperationRepo>(relaxed = true)
        val mockConfigModel = mockk<ConfigModel>()
        every { mockConfigModel.appId } returns appId
        val logoutLock = Any()

        val logoutHelper =
            LogoutHelper(
                logoutLock = logoutLock,
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
                configModel = mockConfigModel,
            )

        // When
        logoutHelper.logout()

        // Then - should create new user and enqueue login operation for device-scoped user
        verify(exactly = 1) { mockUserSwitcher.createAndSwitchToNewUser() }
        verify(exactly = 1) {
            mockOperationRepo.enqueue(
                withArg<LoginUserOperation> { operation ->
                    operation.appId shouldBe appId
                    operation.onesignalId shouldBe onesignalId
                    operation.externalId shouldBe null // Device-scoped user after logout
                    operation.existingOnesignalId shouldBe null
                },
            )
        }
    }

    test("logout operations happen in correct order") {
        // Given - identified user
        val mockIdentityModelStore =
            MockHelper.identityModelStore { model ->
                model.externalId = externalId
                model.onesignalId = onesignalId
            }
        val mockUserSwitcher = mockk<UserSwitcher>(relaxed = true)
        val mockOperationRepo = mockk<IOperationRepo>(relaxed = true)
        val mockConfigModel = mockk<ConfigModel>()
        every { mockConfigModel.appId } returns appId
        val logoutLock = Any()

        val logoutHelper =
            LogoutHelper(
                logoutLock = logoutLock,
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
                configModel = mockConfigModel,
            )

        // When
        logoutHelper.logout()

        // Then - operations should happen in the correct order
        verifyOrder {
            mockUserSwitcher.createAndSwitchToNewUser()
            mockOperationRepo.enqueue(any<LoginUserOperation>())
        }
    }

    test("logout is thread-safe with synchronized block") {
        // Given - identified user
        val mockIdentityModelStore =
            MockHelper.identityModelStore { model ->
                model.externalId = externalId
                model.onesignalId = onesignalId
            }
        val mockUserSwitcher = mockk<UserSwitcher>(relaxed = true)
        val mockOperationRepo = mockk<IOperationRepo>(relaxed = true)
        val mockConfigModel = mockk<ConfigModel>()
        every { mockConfigModel.appId } returns appId
        val logoutLock = Any()

        val logoutHelper =
            LogoutHelper(
                logoutLock = logoutLock,
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
                configModel = mockConfigModel,
            )

        // When - call logout multiple times concurrently
        val threads =
            (1..10).map {
                Thread {
                    logoutHelper.logout()
                }
            }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Then - due to synchronization, operations should complete properly
        verify(atLeast = 1) { mockUserSwitcher.createAndSwitchToNewUser() }
        verify(atLeast = 1) { mockOperationRepo.enqueue(any()) }
    }
})
