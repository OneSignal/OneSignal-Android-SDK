package com.onesignal.user.internal

import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.operations.LoginUserOperation
import com.onesignal.user.internal.properties.PropertiesModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking

/**
 * Unit tests for the LoginHelper class
 *
 * These tests focus on the pure business logic of user login operations,
 * complementing the integration tests in SDKInitTests.kt which test
 * end-to-end SDK initialization and login behavior.
 */
class LoginHelperTests : FunSpec({
    // Test constants - using consistent naming with SDKInitTests
    val appId = "appId"
    val currentExternalId = "current-user"
    val newExternalId = "new-user"
    val currentOneSignalId = "current-onesignal-id"
    val newOneSignalId = "new-onesignal-id"

    beforeEach {
        Logging.logLevel = LogLevel.NONE
    }

    test("login with same external id returns early without creating user") {
        // Given
        val mockIdentityModelStore =
            MockHelper.identityModelStore { model ->
                model.externalId = currentExternalId
                model.onesignalId = currentOneSignalId
            }
        val mockUserSwitcher = mockk<UserSwitcher>(relaxed = true)
        val mockOperationRepo = mockk<IOperationRepo>(relaxed = true)
        val mockConfigModel = mockk<ConfigModel>()
        every { mockConfigModel.appId } returns appId
        val loginLock = Any()

        val loginHelper =
            LoginHelper(
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
                configModel = mockConfigModel,
                loginLock = loginLock,
            )

        // When
        runBlocking {
            loginHelper.login(currentExternalId)
        }

        // Then - should return early without any operations
        verify(exactly = 0) { mockUserSwitcher.createAndSwitchToNewUser(suppressBackendOperation = any(), modify = any()) }
        coVerify(exactly = 0) { mockOperationRepo.enqueueAndWait(any()) }
    }

    test("login with different external id creates and switches to new user") {
        // Given
        val mockIdentityModelStore =
            MockHelper.identityModelStore { model ->
                model.externalId = currentExternalId
                model.onesignalId = currentOneSignalId
            }

        val newIdentityModel =
            IdentityModel().apply {
                externalId = newExternalId
                onesignalId = newOneSignalId
            }

        val mockUserSwitcher = mockk<UserSwitcher>()
        val mockOperationRepo = mockk<IOperationRepo>()
        val mockConfigModel = mockk<ConfigModel>()
        every { mockConfigModel.appId } returns appId
        val loginLock = Any()

        val userSwitcherSlot = slot<(IdentityModel, PropertiesModel) -> Unit>()
        every {
            mockUserSwitcher.createAndSwitchToNewUser(
                suppressBackendOperation = any(),
                modify = capture(userSwitcherSlot),
            )
        } answers {
            userSwitcherSlot.captured(newIdentityModel, PropertiesModel())
            every { mockIdentityModelStore.model } returns newIdentityModel
        }

        coEvery { mockOperationRepo.enqueueAndWait(any()) } returns true

        val loginHelper =
            LoginHelper(
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
                configModel = mockConfigModel,
                loginLock = loginLock,
            )

        // When
        runBlocking {
            loginHelper.login(newExternalId)
        }

        // Then - should switch users and enqueue login operation
        verify(exactly = 1) { mockUserSwitcher.createAndSwitchToNewUser(suppressBackendOperation = any(), modify = any()) }

        userSwitcherSlot.captured(newIdentityModel, PropertiesModel())
        newIdentityModel.externalId shouldBe newExternalId

        coVerify(exactly = 1) {
            mockOperationRepo.enqueueAndWait(
                withArg<LoginUserOperation> { operation ->
                    operation.appId shouldBe appId
                    operation.onesignalId shouldBe newOneSignalId
                    operation.externalId shouldBe newExternalId
//                    operation.existingOneSignalId shouldBe currentOneSignalId
                },
            )
        }
    }

    test("login with null current external id provides existing onesignal id for conversion") {
        // Given - anonymous user (no external ID)
        val mockIdentityModelStore =
            MockHelper.identityModelStore { model ->
                model.externalId = null
                model.onesignalId = currentOneSignalId
            }

        val newIdentityModel =
            IdentityModel().apply {
                externalId = newExternalId
                onesignalId = newOneSignalId
            }

        val mockUserSwitcher = mockk<UserSwitcher>()
        val mockOperationRepo = mockk<IOperationRepo>()
        val mockConfigModel = mockk<ConfigModel>()
        every { mockConfigModel.appId } returns appId
        val loginLock = Any()

        val userSwitcherSlot = slot<(IdentityModel, PropertiesModel) -> Unit>()
        every {
            mockUserSwitcher.createAndSwitchToNewUser(
                suppressBackendOperation = any(),
                modify = capture(userSwitcherSlot),
            )
        } answers {
            userSwitcherSlot.captured(newIdentityModel, PropertiesModel())
            every { mockIdentityModelStore.model } returns newIdentityModel
        }

        coEvery { mockOperationRepo.enqueueAndWait(any()) } returns true

        val loginHelper =
            LoginHelper(
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
                configModel = mockConfigModel,
                loginLock = loginLock,
            )

        // When
        runBlocking {
            loginHelper.login(newExternalId)
        }

        // Then - should provide existing OneSignal ID for anonymous user conversion
        coVerify(exactly = 1) {
            mockOperationRepo.enqueueAndWait(
                withArg<LoginUserOperation> { operation ->
                    operation.appId shouldBe appId
                    operation.onesignalId shouldBe newOneSignalId
                    operation.externalId shouldBe newExternalId
//                    operation.existingOneSignalId shouldBe currentOneSignalId // For conversion
                },
            )
        }
    }

    test("login logs error when operation fails") {
        // Given
        val mockIdentityModelStore =
            MockHelper.identityModelStore { model ->
                model.externalId = currentExternalId
                model.onesignalId = currentOneSignalId
            }

        val newIdentityModel =
            IdentityModel().apply {
                externalId = newExternalId
                onesignalId = newOneSignalId
            }

        val mockUserSwitcher = mockk<UserSwitcher>()
        val mockOperationRepo = mockk<IOperationRepo>()
        val mockConfigModel = mockk<ConfigModel>()
        every { mockConfigModel.appId } returns appId
        val loginLock = Any()

        val userSwitcherSlot = slot<(IdentityModel, PropertiesModel) -> Unit>()
        every {
            mockUserSwitcher.createAndSwitchToNewUser(
                suppressBackendOperation = any(),
                modify = capture(userSwitcherSlot),
            )
        } answers {
            userSwitcherSlot.captured(newIdentityModel, PropertiesModel())
            every { mockIdentityModelStore.model } returns newIdentityModel
        }

        // Mock operation failure
        coEvery { mockOperationRepo.enqueueAndWait(any()) } returns false

        val loginHelper =
            LoginHelper(
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
                configModel = mockConfigModel,
                loginLock = loginLock,
            )

        // When
        runBlocking {
            loginHelper.login(newExternalId)
        }

        // Then - should still switch users but operation fails
        verify(exactly = 1) { mockUserSwitcher.createAndSwitchToNewUser(suppressBackendOperation = any(), modify = any()) }
        coVerify(exactly = 1) { mockOperationRepo.enqueueAndWait(any()) }
    }
})
