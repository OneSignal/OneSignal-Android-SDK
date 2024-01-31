package com.onesignal.user.internal.operations

import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.Operation
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.backend.CreateUserResponse
import com.onesignal.user.internal.backend.IUserBackendService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.backend.PropertiesObject
import com.onesignal.user.internal.backend.SubscriptionObject
import com.onesignal.user.internal.backend.SubscriptionObjectType
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.operations.impl.executors.RefreshUserOperationExecutor
import com.onesignal.user.internal.properties.PropertiesModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.runner.junit4.KotestTestRunner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.runner.RunWith

@RunWith(KotestTestRunner::class)
class RefreshUserOperationExecutorTests : FunSpec({
    val appId = "appId"
    val remoteOneSignalId = "remote-onesignalId"
    val remoteSubscriptionId1 = "remote-subscriptionId1"
    val remoteSubscriptionId2 = "remote-subscriptionId2"

    test("refresh user is successful") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId) } returns
            CreateUserResponse(
                mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId, "aliasLabel1" to "aliasValue1"),
                PropertiesObject(country = "US"),
                listOf(SubscriptionObject(remoteSubscriptionId1, SubscriptionObjectType.ANDROID_PUSH, enabled = true, token = "pushToken"), SubscriptionObject(remoteSubscriptionId2, SubscriptionObjectType.EMAIL, token = "name@company.com"))
            )

        /* Given */
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockIdentityModel = IdentityModel()
        mockIdentityModel.onesignalId = remoteOneSignalId
        every { mockIdentityModelStore.model } returns mockIdentityModel
        every { mockIdentityModelStore.replace(any(), any()) } just runs

        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockPropertiesModel = PropertiesModel()
        mockPropertiesModel.onesignalId = remoteOneSignalId
        mockPropertiesModel.country = "VT"
        mockPropertiesModel.language = "language"
        every { mockPropertiesModelStore.model } returns mockPropertiesModel
        every { mockPropertiesModelStore.replace(any(), any()) } just runs

        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        every { mockSubscriptionsModelStore.replaceAll(any(), any()) } just runs

        val loginUserOperationExecutor = RefreshUserOperationExecutor(
            mockUserBackendService,
            mockIdentityModelStore,
            mockPropertiesModelStore,
            mockSubscriptionsModelStore
        )

        val operations = listOf<Operation>(RefreshUserOperation(appId, remoteOneSignalId))

        /* When */
        val response = loginUserOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.SUCCESS
        coVerify(exactly = 1) {
            mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId)
            mockIdentityModelStore.replace(
                withArg {
                    it["aliasLabel1"] shouldBe "aliasValue1"
                },
                ModelChangeTags.HYDRATE
            )
            mockPropertiesModelStore.replace(
                withArg {
                    it.country shouldBe "US"
                    it.language shouldBe null
                },
                ModelChangeTags.HYDRATE
            )
            mockSubscriptionsModelStore.replaceAll(
                withArg {
                    it.count() shouldBe 2
                    it[0].id shouldBe remoteSubscriptionId1
                    it[0].type shouldBe SubscriptionType.PUSH
                    it[0].optedIn shouldBe true
                    it[0].address shouldBe "pushToken"
                    it[1].id shouldBe remoteSubscriptionId2
                    it[1].type shouldBe SubscriptionType.EMAIL
                    it[1].optedIn shouldBe true
                    it[1].address shouldBe "name@company.com"
                },
                ModelChangeTags.HYDRATE
            )
        }
    }

    test("refresh user does not hydrate user when user has changed") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId) } returns
            CreateUserResponse(
                mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId),
                PropertiesObject(),
                listOf()
            )

        /* Given */
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockIdentityModel = IdentityModel()
        mockIdentityModel.onesignalId = "new-onesignalId"
        every { mockIdentityModelStore.model } returns mockIdentityModel

        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockPropertiesModel = PropertiesModel()
        mockPropertiesModel.onesignalId = "new-onesignalId"
        mockPropertiesModel.country = "US"
        every { mockPropertiesModelStore.model } returns mockPropertiesModel

        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor = RefreshUserOperationExecutor(
            mockUserBackendService,
            mockIdentityModelStore,
            mockPropertiesModelStore,
            mockSubscriptionsModelStore
        )

        val operations = listOf<Operation>(RefreshUserOperation(appId, remoteOneSignalId))

        /* When */
        val response = loginUserOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.SUCCESS
        mockIdentityModel.onesignalId shouldBe "new-onesignalId"
        mockPropertiesModel.onesignalId shouldBe "new-onesignalId"
        mockPropertiesModel.country shouldBe "US"
        coVerify(exactly = 1) {
            mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId)
        }
    }

    test("refresh user fails with retry when there is a network condition") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId) } throws BackendException(408)

        /* Given */
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor = RefreshUserOperationExecutor(
            mockUserBackendService,
            mockIdentityModelStore,
            mockPropertiesModelStore,
            mockSubscriptionsModelStore
        )

        val operations = listOf<Operation>(RefreshUserOperation(appId, remoteOneSignalId))

        /* When */
        val response = loginUserOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.FAIL_RETRY
        coVerify(exactly = 1) {
            mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId)
        }
    }

    test("refresh user fails without retry when there is a backend error condition") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId) } throws BackendException(404)

        /* Given */
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor = RefreshUserOperationExecutor(
            mockUserBackendService,
            mockIdentityModelStore,
            mockPropertiesModelStore,
            mockSubscriptionsModelStore
        )

        val operations = listOf<Operation>(RefreshUserOperation(appId, remoteOneSignalId))

        /* When */
        val response = loginUserOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.FAIL_NORETRY
        coVerify(exactly = 1) {
            mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId)
        }
    }
})
