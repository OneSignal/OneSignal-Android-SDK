package com.onesignal.user.internal

import com.onesignal.OneSignalUserProfile
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.OperationWaitResult
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.jwt.JwtRequirement
import com.onesignal.user.internal.jwt.JwtTokenStore
import com.onesignal.user.internal.operations.LoginUserOperation
import com.onesignal.user.internal.properties.PropertiesModel
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionType
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
        every { mockConfigModel.useIdentityVerification } returns JwtRequirement.NOT_REQUIRED
        val loginLock = Any()

        val loginHelper =
            LoginHelper(
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
                configModel = mockConfigModel,
                jwtTokenStore = JwtTokenStore(MockPreferencesService()),
                lock = loginLock,
                subscriptionModelStore = mockk(relaxed = true),
            )

        // When
        runBlocking {
            val context = loginHelper.switchUser(currentExternalId)
            if (context != null) loginHelper.enqueueLogin(context)
        }

        // Then - should return early without any operations
        verify(exactly = 0) { mockUserSwitcher.createAndSwitchToNewUser(suppressBackendOperation = any(), modify = any()) }
        coVerify(exactly = 0) { mockOperationRepo.enqueueAndAwaitResult(any()) }
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
        every { mockConfigModel.useIdentityVerification } returns JwtRequirement.NOT_REQUIRED
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

        coEvery { mockOperationRepo.enqueueAndAwaitResult(any()) } returns OperationWaitResult(true)

        val loginHelper =
            LoginHelper(
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
                configModel = mockConfigModel,
                jwtTokenStore = JwtTokenStore(MockPreferencesService()),
                lock = loginLock,
                subscriptionModelStore = mockk(relaxed = true),
            )

        // When
        runBlocking {
            val context = loginHelper.switchUser(newExternalId)
            if (context != null) loginHelper.enqueueLogin(context)
        }

        // Then - should switch users and enqueue login operation
        verify(exactly = 1) { mockUserSwitcher.createAndSwitchToNewUser(suppressBackendOperation = any(), modify = any()) }

        userSwitcherSlot.captured(newIdentityModel, PropertiesModel())
        newIdentityModel.externalId shouldBe newExternalId

        coVerify(exactly = 1) {
            mockOperationRepo.enqueueAndAwaitResult(
                withArg<LoginUserOperation> { operation ->
                    operation.appId shouldBe appId
                    operation.onesignalId shouldBe newOneSignalId
                    operation.externalId shouldBe newExternalId
                    operation.existingOnesignalId shouldBe null // Current user already has external ID, so no existing OneSignal ID
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
        every { mockConfigModel.useIdentityVerification } returns JwtRequirement.NOT_REQUIRED
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

        coEvery { mockOperationRepo.enqueueAndAwaitResult(any()) } returns OperationWaitResult(true)

        val loginHelper =
            LoginHelper(
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
                configModel = mockConfigModel,
                jwtTokenStore = JwtTokenStore(MockPreferencesService()),
                lock = loginLock,
                subscriptionModelStore = mockk(relaxed = true),
            )

        // When
        runBlocking {
            val context = loginHelper.switchUser(newExternalId)
            if (context != null) loginHelper.enqueueLogin(context)
        }

        // Then - should provide existing OneSignal ID for anonymous user conversion
        coVerify(exactly = 1) {
            mockOperationRepo.enqueueAndAwaitResult(
                withArg<LoginUserOperation> { operation ->
                    operation.appId shouldBe appId
                    operation.onesignalId shouldBe newOneSignalId
                    operation.externalId shouldBe newExternalId
                    operation.existingOnesignalId shouldBe currentOneSignalId // For conversion
                },
            )
        }
    }

    test("login under IV-required does NOT carry existingOnesignalId from anonymous user") {
        // Given - anonymous user, IV is REQUIRED. The anon user was never created server-side
        // (no JWT), so its onesignalId would be permanently local. Carrying it as
        // existingOnesignalId on the new LoginUserOperation would deadlock canStartExecute.
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
        every { mockConfigModel.useIdentityVerification } returns JwtRequirement.REQUIRED

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

        coEvery { mockOperationRepo.enqueueAndAwaitResult(any()) } returns OperationWaitResult(true)

        val loginHelper =
            LoginHelper(
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
                configModel = mockConfigModel,
                jwtTokenStore = JwtTokenStore(MockPreferencesService()),
                lock = Any(),
                subscriptionModelStore = mockk(relaxed = true),
            )

        // When
        runBlocking {
            val context = loginHelper.switchUser(newExternalId, jwtBearerToken = "fresh-jwt")
            if (context != null) loginHelper.enqueueLogin(context)
        }

        // Then — under IV, the executor must take the createUser (upsert) path; no merge link.
        coVerify(exactly = 1) {
            mockOperationRepo.enqueueAndAwaitResult(
                withArg<LoginUserOperation> { operation ->
                    operation.externalId shouldBe newExternalId
                    operation.existingOnesignalId shouldBe null
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
        every { mockConfigModel.useIdentityVerification } returns JwtRequirement.NOT_REQUIRED
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
        coEvery { mockOperationRepo.enqueueAndAwaitResult(any()) } returns
            OperationWaitResult(false, httpStatusCode = 400, httpResponse = """{"errors":["invalid phone"]}""")

        val loginHelper =
            LoginHelper(
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
                configModel = mockConfigModel,
                jwtTokenStore = JwtTokenStore(MockPreferencesService()),
                lock = loginLock,
                subscriptionModelStore = mockk(relaxed = true),
            )

        // When
        val waitResult =
            runBlocking {
                val context = loginHelper.switchUser(newExternalId)
                loginHelper.enqueueLogin(context!!)
            }

        // Then - should still switch users but operation fails with the backend body
        waitResult.success shouldBe false
        waitResult.httpStatusCode shouldBe 400
        waitResult.httpResponse shouldBe """{"errors":["invalid phone"]}"""
        verify(exactly = 1) { mockUserSwitcher.createAndSwitchToNewUser(suppressBackendOperation = any(), modify = any()) }
        coVerify(exactly = 1) { mockOperationRepo.enqueueAndAwaitResult(any()) }
    }

    test("login with JWT stores token in JwtTokenStore before enqueueing op") {
        // Given
        val mockIdentityModelStore =
            MockHelper.identityModelStore { model ->
                model.externalId = null
                model.onesignalId = currentOneSignalId
            }
        val mockUserSwitcher = mockk<UserSwitcher>(relaxed = true)
        every {
            mockUserSwitcher.createAndSwitchToNewUser(suppressBackendOperation = any(), modify = any())
        } answers {
            val modifier = arg<(IdentityModel, PropertiesModel) -> Unit>(1)
            val newIdentity = mockk<IdentityModel>(relaxed = true)
            val newProperties = mockk<PropertiesModel>(relaxed = true)
            modifier(newIdentity, newProperties)
            mockIdentityModelStore.model.onesignalId = newOneSignalId
            mockIdentityModelStore.model.externalId = newExternalId
        }
        val mockOperationRepo = mockk<IOperationRepo>(relaxed = true)
        coEvery { mockOperationRepo.enqueueAndAwaitResult(any()) } returns OperationWaitResult(true)
        val mockConfigModel = mockk<ConfigModel>()
        every { mockConfigModel.appId } returns appId
        every { mockConfigModel.useIdentityVerification } returns JwtRequirement.NOT_REQUIRED
        val jwtTokenStore = JwtTokenStore(MockPreferencesService())

        val loginHelper =
            LoginHelper(
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
                configModel = mockConfigModel,
                jwtTokenStore = jwtTokenStore,
                lock = Any(),
                subscriptionModelStore = mockk(relaxed = true),
            )

        // When
        runBlocking {
            val context = loginHelper.switchUser(newExternalId, jwtBearerToken = "the-jwt")
            if (context != null) loginHelper.enqueueLogin(context)
        }

        // Then: JWT was stored under the new externalId.
        jwtTokenStore.getJwt(newExternalId) shouldBe "the-jwt"
    }

    test("login with same externalId + new JWT updates the stored token") {
        // Given: already logged in as currentExternalId
        val mockIdentityModelStore =
            MockHelper.identityModelStore { model ->
                model.externalId = currentExternalId
                model.onesignalId = currentOneSignalId
            }
        val mockUserSwitcher = mockk<UserSwitcher>(relaxed = true)
        val mockOperationRepo = mockk<IOperationRepo>(relaxed = true)
        val mockConfigModel = mockk<ConfigModel>()
        every { mockConfigModel.appId } returns appId
        every { mockConfigModel.useIdentityVerification } returns JwtRequirement.NOT_REQUIRED
        val jwtTokenStore = JwtTokenStore(MockPreferencesService())
        jwtTokenStore.putJwt(currentExternalId, "old-jwt")

        val loginHelper =
            LoginHelper(
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
                configModel = mockConfigModel,
                jwtTokenStore = jwtTokenStore,
                lock = Any(),
                subscriptionModelStore = mockk(relaxed = true),
            )

        // When: login with same externalId but new JWT
        runBlocking {
            val context = loginHelper.switchUser(currentExternalId, jwtBearerToken = "new-jwt")
            if (context != null) loginHelper.enqueueLogin(context)
        }

        // Then: no user-switch happened, JWT was refreshed, and the queue was woken so any
        // ops deferred by hasValidJwtIfRequired dispatch immediately.
        verify(exactly = 0) { mockUserSwitcher.createAndSwitchToNewUser(suppressBackendOperation = any(), modify = any()) }
        jwtTokenStore.getJwt(currentExternalId) shouldBe "new-jwt"
        verify(exactly = 1) { mockOperationRepo.forceExecuteOperations() }
    }

    test("enqueueLogin copies profile fields onto LoginUserOperation") {
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
        every { mockConfigModel.useIdentityVerification } returns JwtRequirement.NOT_REQUIRED
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
        coEvery { mockOperationRepo.enqueueAndAwaitResult(any()) } returns OperationWaitResult(true)

        val loginHelper =
            LoginHelper(
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
                configModel = mockConfigModel,
                jwtTokenStore = JwtTokenStore(MockPreferencesService()),
                lock = Any(),
                subscriptionModelStore = mockk(relaxed = true),
            )

        runBlocking {
            val context = loginHelper.switchUser(newExternalId)!!
            loginHelper.enqueueLogin(context, OneSignalUserProfile(email = "a@b.com", tags = mapOf("plan" to "pro")))
        }

        coVerify(exactly = 1) {
            mockOperationRepo.enqueueAndAwaitResult(
                withArg<LoginUserOperation> { operation ->
                    operation.email shouldBe "a@b.com"
                    operation.tags shouldBe mapOf("plan" to "pro")
                    operation.externalId shouldBe newExternalId
                },
            )
        }
    }

    test("same external id with a profile still enqueues login") {
        val mockIdentityModelStore =
            MockHelper.identityModelStore { model ->
                model.externalId = currentExternalId
                model.onesignalId = currentOneSignalId
            }
        val mockUserSwitcher = mockk<UserSwitcher>(relaxed = true)
        val mockOperationRepo = mockk<IOperationRepo>()
        val mockConfigModel = mockk<ConfigModel>()
        every { mockConfigModel.appId } returns appId
        every { mockConfigModel.useIdentityVerification } returns JwtRequirement.NOT_REQUIRED
        coEvery { mockOperationRepo.enqueueAndAwaitResult(any()) } returns OperationWaitResult(true)

        val loginHelper =
            LoginHelper(
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
                configModel = mockConfigModel,
                jwtTokenStore = JwtTokenStore(MockPreferencesService()),
                lock = Any(),
                subscriptionModelStore = mockk(relaxed = true),
            )

        runBlocking {
            val context =
                loginHelper.switchUser(currentExternalId)
                    ?: loginHelper.contextForCurrentUser(currentExternalId)
            loginHelper.enqueueLogin(context, OneSignalUserProfile(email = "a@b.com"))
        }

        verify(exactly = 0) { mockUserSwitcher.createAndSwitchToNewUser(suppressBackendOperation = any(), modify = any()) }
        coVerify(exactly = 1) {
            mockOperationRepo.enqueueAndAwaitResult(
                withArg<LoginUserOperation> { operation ->
                    operation.onesignalId shouldBe currentOneSignalId
                    operation.externalId shouldBe currentExternalId
                    operation.existingOnesignalId shouldBe null
                    operation.email shouldBe "a@b.com"
                },
            )
        }
    }

    test("loginDataFromStores fills email and SMS ids from matching addresses") {
        val identityStore =
            MockHelper.identityModelStore { model ->
                model.externalId = currentExternalId
                model.onesignalId = currentOneSignalId
            }
        val subscriptions = SubscriptionModelStore(MockPreferencesService())
        subscriptions.add(
            SubscriptionModel().apply {
                id = "email-id"
                type = SubscriptionType.EMAIL
                address = "a@b.com"
            },
        )
        subscriptions.add(
            SubscriptionModel().apply {
                id = "sms-id"
                type = SubscriptionType.SMS
                address = "+15555550100"
            },
        )
        val loginHelper =
            LoginHelper(
                identityModelStore = identityStore,
                userSwitcher = mockk(relaxed = true),
                operationRepo = mockk(relaxed = true),
                configModel = mockk(relaxed = true),
                jwtTokenStore = JwtTokenStore(MockPreferencesService()),
                lock = Any(),
                subscriptionModelStore = subscriptions,
            )

        val data =
            loginHelper.loginDataFromStores(
                currentExternalId,
                OneSignalUserProfile(email = "a@b.com", phoneNumber = "+15555550100"),
            )

        data.onesignalId shouldBe currentOneSignalId
        data.externalId shouldBe currentExternalId
        data.emailSubscriptionId shouldBe "email-id"
        data.smsSubscriptionId shouldBe "sms-id"
        loginHelper.loginDataFromStores(currentExternalId, OneSignalUserProfile()).emailSubscriptionId shouldBe null
    }
})
