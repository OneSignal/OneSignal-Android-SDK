package com.onesignal.user.internal.operations

import com.onesignal.common.TimeUtils
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
import com.onesignal.user.internal.builduser.IRebuildUserService
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.operations.ExecutorMocks.Companion.getNewRecordState
import com.onesignal.user.internal.operations.impl.executors.RefreshUserOperationExecutor
import com.onesignal.user.internal.properties.PropertiesModel
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.internal.subscriptions.SubscriptionType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject

class RefreshUserOperationExecutorTests : FunSpec({
    val appId = "appId"
    val existingSubscriptionId1 = "existing-subscriptionId1"
    val onDevicePushToken = "on-device-push-token"
    val remoteOneSignalId = "remote-onesignalId"
    val remoteSubscriptionId1 = "remote-subscriptionId1"
    val remoteSubscriptionId2 = "remote-subscriptionId2"

    test("refresh user is successful") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId) } returns
            CreateUserResponse(
                mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId, "aliasLabel1" to "aliasValue1"),
                PropertiesObject(country = "US"),
                listOf(
                    SubscriptionObject(existingSubscriptionId1, SubscriptionObjectType.ANDROID_PUSH, enabled = true, token = "on-backend-push-token"),
                    SubscriptionObject(remoteSubscriptionId1, SubscriptionObjectType.ANDROID_PUSH, enabled = true, token = "pushToken2"),
                    SubscriptionObject(remoteSubscriptionId2, SubscriptionObjectType.EMAIL, token = "name@company.com"),
                ),
            )

        // Given
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

        val mockPushSubscriptionModel = SubscriptionModel()
        mockPushSubscriptionModel.id = existingSubscriptionId1
        mockPushSubscriptionModel.type = SubscriptionType.PUSH
        mockPushSubscriptionModel.address = onDevicePushToken
        mockPushSubscriptionModel.status = SubscriptionStatus.SUBSCRIBED
        mockPushSubscriptionModel.optedIn = true
        every { mockSubscriptionsModelStore.get(existingSubscriptionId1) } returns mockPushSubscriptionModel

        val mockConfigModelStore =
            MockHelper.configModelStore {
                it.pushSubscriptionId = existingSubscriptionId1
            }

        val mockBuildUserService = mockk<IRebuildUserService>()

        val loginUserOperationExecutor =
            RefreshUserOperationExecutor(
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                mockConfigModelStore,
                mockBuildUserService,
                getNewRecordState(),
            )

        val operations = listOf<Operation>(RefreshUserOperation(appId, remoteOneSignalId))

        // When
        val response = loginUserOperationExecutor.execute(operations)

        // Then
        response.result shouldBe ExecutionResult.SUCCESS
        coVerify(exactly = 1) {
            mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId)
            mockIdentityModelStore.replace(
                withArg {
                    it["aliasLabel1"] shouldBe "aliasValue1"
                },
                ModelChangeTags.HYDRATE,
            )
            mockPropertiesModelStore.replace(
                withArg {
                    it.country shouldBe "US"
                    it.language shouldBe null
                },
                ModelChangeTags.HYDRATE,
            )
            mockSubscriptionsModelStore.replaceAll(
                withArg {
                    it.count() shouldBe 2
                    it[0].id shouldBe remoteSubscriptionId2
                    it[0].type shouldBe SubscriptionType.EMAIL
                    it[0].optedIn shouldBe true
                    it[0].address shouldBe "name@company.com"
                    it[1].id shouldBe existingSubscriptionId1
                    it[1].type shouldBe SubscriptionType.PUSH
                    it[1].optedIn shouldBe true
                    it[1].address shouldBe onDevicePushToken
                },
                ModelChangeTags.HYDRATE,
            )
        }
    }

    test("refresh user does not hydrate user when user has changed") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId) } returns
            CreateUserResponse(
                mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId),
                PropertiesObject(),
                listOf(),
            )

        // Given
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
        val mockBuildUserService = mockk<IRebuildUserService>()

        val loginUserOperationExecutor =
            RefreshUserOperationExecutor(
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                MockHelper.configModelStore(),
                mockBuildUserService,
                getNewRecordState(),
            )

        val operations = listOf<Operation>(RefreshUserOperation(appId, remoteOneSignalId))

        // When
        val response = loginUserOperationExecutor.execute(operations)

        // Then
        response.result shouldBe ExecutionResult.SUCCESS
        mockIdentityModel.onesignalId shouldBe "new-onesignalId"
        mockPropertiesModel.onesignalId shouldBe "new-onesignalId"
        mockPropertiesModel.country shouldBe "US"
        coVerify(exactly = 1) {
            mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId)
        }
    }

    test("refresh user fails with retry when there is a network condition") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery {
            mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId)
        } throws BackendException(408, retryAfterSeconds = 10)

        // Given
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        val mockBuildUserService = mockk<IRebuildUserService>()

        val loginUserOperationExecutor =
            RefreshUserOperationExecutor(
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                MockHelper.configModelStore(),
                mockBuildUserService,
                getNewRecordState(),
            )

        val operations = listOf<Operation>(RefreshUserOperation(appId, remoteOneSignalId))

        // When
        val response = loginUserOperationExecutor.execute(operations)

        // Then
        response.result shouldBe ExecutionResult.FAIL_RETRY
        response.retryAfterSeconds shouldBe 10
        coVerify(exactly = 1) {
            mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId)
        }
    }

    test("refresh user fails without retry when there is a backend error condition") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId) } throws BackendException(400)

        // Given
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        val mockBuildUserService = mockk<IRebuildUserService>()

        val loginUserOperationExecutor =
            RefreshUserOperationExecutor(
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                MockHelper.configModelStore(),
                mockBuildUserService,
                getNewRecordState(),
            )

        val operations = listOf<Operation>(RefreshUserOperation(appId, remoteOneSignalId))

        // When
        val response = loginUserOperationExecutor.execute(operations)

        // Then
        response.result shouldBe ExecutionResult.FAIL_NORETRY
        coVerify(exactly = 1) {
            mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId)
        }
    }

    test("refresh user fails without retry when backend returns MISSING") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId) } throws BackendException(404)

        // Given
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        val mockBuildUserService = mockk<IRebuildUserService>()
        every { mockBuildUserService.getRebuildOperationsIfCurrentUser(any(), any()) } returns null

        val loginUserOperationExecutor =
            RefreshUserOperationExecutor(
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                MockHelper.configModelStore(),
                mockBuildUserService,
                getNewRecordState(),
            )

        val operations = listOf<Operation>(RefreshUserOperation(appId, remoteOneSignalId))

        // When
        val response = loginUserOperationExecutor.execute(operations)

        // Then
        response.result shouldBe ExecutionResult.FAIL_NORETRY
        coVerify(exactly = 1) {
            mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId)
        }
    }

    test("refresh user is retried when backend returns MISSING, but isInMissingRetryWindow") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId) } throws BackendException(404)

        // Given
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        val mockBuildUserService = mockk<IRebuildUserService>()

        val mockConfigModelStore = MockHelper.configModelStore().also { it.model.opRepoPostCreateRetryUpTo = 1_000 }
        val newRecordState = getNewRecordState(mockConfigModelStore).also { it.add(remoteOneSignalId) }

        val loginUserOperationExecutor =
            RefreshUserOperationExecutor(
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                MockHelper.configModelStore(),
                mockBuildUserService,
                newRecordState,
            )

        val operations = listOf<Operation>(RefreshUserOperation(appId, remoteOneSignalId))

        // When
        val response = loginUserOperationExecutor.execute(operations)

        // Then
        response.result shouldBe ExecutionResult.FAIL_RETRY
        coVerify(exactly = 1) {
            mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId)
        }
    }

    test("refresh user sets local timezone via propertiesModel update") {
        // Given
        val mockTimeZone = "America/New_York"
        mockkObject(TimeUtils)
        every { TimeUtils.getTimeZoneId() } returns mockTimeZone

        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId) } returns
            CreateUserResponse(
                mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId),
                PropertiesObject(country = "US"),
                listOf(),
            )

        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockIdentityModel = IdentityModel()
        mockIdentityModel.onesignalId = remoteOneSignalId
        every { mockIdentityModelStore.model } returns mockIdentityModel
        every { mockIdentityModelStore.replace(any(), any()) } just runs

        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockPropertiesModel = PropertiesModel()
        mockPropertiesModel.onesignalId = remoteOneSignalId
        every { mockPropertiesModelStore.model } returns mockPropertiesModel
        every { mockPropertiesModelStore.replace(any(), any()) } just runs

        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        every { mockSubscriptionsModelStore.replaceAll(any(), any()) } just runs

        val mockConfigModelStore = MockHelper.configModelStore()
        val mockBuildUserService = mockk<IRebuildUserService>()

        val refreshUserOperationExecutor =
            RefreshUserOperationExecutor(
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                mockConfigModelStore,
                mockBuildUserService,
                getNewRecordState(),
            )

        val operations = listOf<Operation>(RefreshUserOperation(appId, remoteOneSignalId))

        try {
            // When
            val response = refreshUserOperationExecutor.execute(operations)

            // Then - Verify success and that timezone is set to our mocked value (via update() call)
            response.result shouldBe ExecutionResult.SUCCESS
            coVerify(exactly = 1) {
                mockPropertiesModelStore.replace(
                    withArg {
                        it.country shouldBe "US"
                        // Verify timezone is set to our mocked timezone (what update() does)
                        it.timezone shouldBe mockTimeZone
                    },
                    ModelChangeTags.HYDRATE,
                )
            }
        } finally {
            // Clean up the mock
            unmockkObject(TimeUtils)
        }
    }
})
