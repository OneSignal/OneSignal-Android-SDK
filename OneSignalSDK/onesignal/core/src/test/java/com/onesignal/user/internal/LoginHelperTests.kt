package com.onesignal.user.internal

import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.identity.JwtTokenStore
import com.onesignal.user.internal.operations.LoginUserOperation
import com.onesignal.user.internal.properties.PropertiesModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking

class LoginHelperTests : FunSpec({
    val appId = "appId"
    val currentExternalId = "current-user"
    val newExternalId = "new-user"
    val currentOneSignalId = "current-onesignal-id"
    val newOneSignalId = "new-onesignal-id"

    beforeEach {
        Logging.logLevel = LogLevel.NONE
    }

    fun createLoginHelper(
        identityModelStore: com.onesignal.user.internal.identity.IdentityModelStore,
        userSwitcher: UserSwitcher = mockk(relaxed = true),
        operationRepo: IOperationRepo = mockk(relaxed = true),
        configModel: ConfigModel = mockk<ConfigModel>().also {
            every { it.appId } returns appId
            every { it.useIdentityVerification } returns null
        },
        jwtTokenStore: JwtTokenStore = mockk(relaxed = true),
        lock: Any = Any(),
    ) = LoginHelper(
        identityModelStore = identityModelStore,
        userSwitcher = userSwitcher,
        operationRepo = operationRepo,
        configModel = configModel,
        jwtTokenStore = jwtTokenStore,
        lock = lock,
    )

    test("switchUser with same external id returns null without creating user") {
        val mockIdentityModelStore =
            MockHelper.identityModelStore { model ->
                model.externalId = currentExternalId
                model.onesignalId = currentOneSignalId
            }
        val mockUserSwitcher = mockk<UserSwitcher>(relaxed = true)
        val mockOperationRepo = mockk<IOperationRepo>(relaxed = true)

        val loginHelper =
            createLoginHelper(
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
            )

        val context = loginHelper.switchUser(currentExternalId)

        context shouldBe null
        verify(exactly = 0) { mockUserSwitcher.createAndSwitchToNewUser(suppressBackendOperation = any(), modify = any()) }
        coVerify(exactly = 0) { mockOperationRepo.enqueueAndWait(any()) }
    }

    test("switchUser with different external id creates and switches to new user") {
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
            createLoginHelper(
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
            )

        val context = loginHelper.switchUser(newExternalId)

        context shouldNotBe null
        context!!.appId shouldBe appId
        context.newIdentityOneSignalId shouldBe newOneSignalId
        context.externalId shouldBe newExternalId

        verify(exactly = 1) { mockUserSwitcher.createAndSwitchToNewUser(suppressBackendOperation = any(), modify = any()) }

        userSwitcherSlot.captured(newIdentityModel, PropertiesModel())
        newIdentityModel.externalId shouldBe newExternalId
    }

    test("enqueueLogin enqueues login operation and returns") {
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
            createLoginHelper(
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
            )

        val context = loginHelper.switchUser(newExternalId)!!
        runBlocking {
            loginHelper.enqueueLogin(context)
        }

        coVerify(exactly = 1) {
            mockOperationRepo.enqueueAndWait(
                withArg<LoginUserOperation> { operation ->
                    operation.appId shouldBe appId
                    operation.onesignalId shouldBe newOneSignalId
                    operation.externalId shouldBe newExternalId
                    operation.existingOnesignalId shouldBe null
                },
            )
        }
    }

    test("switchUser with null current external id provides existing onesignal id for conversion") {
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
        every { mockConfigModel.useIdentityVerification } returns false

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
            createLoginHelper(
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
                configModel = mockConfigModel,
            )

        val context = loginHelper.switchUser(newExternalId)!!
        runBlocking {
            loginHelper.enqueueLogin(context)
        }

        coVerify(exactly = 1) {
            mockOperationRepo.enqueueAndWait(
                withArg<LoginUserOperation> { operation ->
                    operation.appId shouldBe appId
                    operation.onesignalId shouldBe newOneSignalId
                    operation.externalId shouldBe newExternalId
                    operation.existingOnesignalId shouldBe currentOneSignalId
                },
            )
        }
    }

    test("enqueueLogin logs warning when operation fails") {
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

        coEvery { mockOperationRepo.enqueueAndWait(any()) } returns false

        val loginHelper =
            createLoginHelper(
                identityModelStore = mockIdentityModelStore,
                userSwitcher = mockUserSwitcher,
                operationRepo = mockOperationRepo,
            )

        val context = loginHelper.switchUser(newExternalId)!!
        runBlocking {
            loginHelper.enqueueLogin(context)
        }

        verify(exactly = 1) { mockUserSwitcher.createAndSwitchToNewUser(suppressBackendOperation = any(), modify = any()) }
        coVerify(exactly = 1) { mockOperationRepo.enqueueAndWait(any()) }
    }
})
