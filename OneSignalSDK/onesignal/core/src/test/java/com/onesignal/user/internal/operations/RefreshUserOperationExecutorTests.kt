package com.onesignal.user.internal.operations

import com.onesignal.common.TimeUtils
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.backend.CreateUserResponse
import com.onesignal.user.internal.backend.IUserBackendService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.backend.PropertiesObject
import com.onesignal.user.internal.backend.SubscriptionObject
import com.onesignal.user.internal.backend.SubscriptionObjectType
import com.onesignal.user.internal.builduser.IRebuildUserService
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.operations.ExecutorMocks.Companion.getIdentityVerificationService
import com.onesignal.user.internal.operations.ExecutorMocks.Companion.getJwtTokenStore
import com.onesignal.user.internal.operations.ExecutorMocks.Companion.getNewRecordState
import com.onesignal.user.internal.operations.impl.executors.RefreshUserOperationExecutor
import com.onesignal.user.internal.operations.impl.executors.SubscriptionOperationExecutor
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

    test("refresh user is successful and models are hydrated properly") {
        // Given
        val localTimeZone = "Europe/Local"
        val remoteTimeZone = "Europe/Remote"
        mockkObject(TimeUtils)
        every { TimeUtils.getTimeZoneId() } returns localTimeZone

        val localCountry = "US"
        val remoteCountry = "VT"
        val localLanguage = "fr"
        val remoteLanguage = "it"
        val remoteTags = mapOf("tagKey1" to "remote-1", "tagKey2" to "remote-2")

        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId) } returns
            CreateUserResponse(
                mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId, "aliasLabel1" to "aliasValue1"),
                PropertiesObject(country = remoteCountry, language = remoteLanguage, timezoneId = remoteTimeZone, tags = remoteTags),
                listOf(
                    // notificationTypes = 1 keeps server-side view healthy so the push
                    // self-heal divergence check is a no-op for this happy-path test.
                    SubscriptionObject(existingSubscriptionId1, SubscriptionObjectType.ANDROID_PUSH, enabled = true, notificationTypes = 1, token = "on-backend-push-token"),
                    SubscriptionObject(remoteSubscriptionId1, SubscriptionObjectType.ANDROID_PUSH, enabled = true, notificationTypes = 1, token = "pushToken2"),
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
        mockPropertiesModel.country = localCountry
        mockPropertiesModel.language = localLanguage
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

        val refreshUserOperationExecutor =
            RefreshUserOperationExecutor(
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                mockConfigModelStore,
                mockBuildUserService,
                getNewRecordState(),
                getJwtTokenStore(), getIdentityVerificationService(),
            )

        val operations = listOf<Operation>(RefreshUserOperation(appId, remoteOneSignalId, null))

        try {
            // When
            val response = refreshUserOperationExecutor.execute(operations)

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
                // The properties model should be set with appropriate remote and local values
                mockPropertiesModelStore.replace(
                    withArg {
                        it.onesignalId shouldBe remoteOneSignalId
                        it.country shouldBe remoteCountry
                        it.language shouldBe remoteLanguage
                        it.tags shouldBe remoteTags
                        it.timezone shouldBe localTimeZone // timezone is set locally
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
        } finally {
            // Clean up the mock
            unmockkObject(TimeUtils)
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

        val refreshUserOperationExecutor =
            RefreshUserOperationExecutor(
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                MockHelper.configModelStore(),
                mockBuildUserService,
                getNewRecordState(),
                getJwtTokenStore(), getIdentityVerificationService(),
            )

        val operations = listOf<Operation>(RefreshUserOperation(appId, remoteOneSignalId, null))

        // When
        val response = refreshUserOperationExecutor.execute(operations)

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

        val refreshUserOperationExecutor =
            RefreshUserOperationExecutor(
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                MockHelper.configModelStore(),
                mockBuildUserService,
                getNewRecordState(),
                getJwtTokenStore(), getIdentityVerificationService(),
            )

        val operations = listOf<Operation>(RefreshUserOperation(appId, remoteOneSignalId, null))

        // When
        val response = refreshUserOperationExecutor.execute(operations)

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

        val refreshUserOperationExecutor =
            RefreshUserOperationExecutor(
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                MockHelper.configModelStore(),
                mockBuildUserService,
                getNewRecordState(),
                getJwtTokenStore(), getIdentityVerificationService(),
            )

        val operations = listOf<Operation>(RefreshUserOperation(appId, remoteOneSignalId, null))

        // When
        val response = refreshUserOperationExecutor.execute(operations)

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

        val refreshUserOperationExecutor =
            RefreshUserOperationExecutor(
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                MockHelper.configModelStore(),
                mockBuildUserService,
                getNewRecordState(),
                getJwtTokenStore(), getIdentityVerificationService(),
            )

        val operations = listOf<Operation>(RefreshUserOperation(appId, remoteOneSignalId, null))

        // When
        val response = refreshUserOperationExecutor.execute(operations)

        // Then
        response.result shouldBe ExecutionResult.FAIL_NORETRY
        coVerify(exactly = 1) {
            mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId)
        }
    }

    // Push self-heal divergence detection. Verifies that when the device-cached push
    // subscription resolves to enabled-and-opted-in but the GET /users response returns the
    // same subscription as disabled (the "Never Subscribed" stuck state),
    // RefreshUserOperationExecutor emits a follow-up UpdateSubscriptionOperation to re-assert
    // local truth via PATCH.
    fun buildSelfHealHarness(
        serverPushEnabled: Boolean,
        serverNotificationTypes: Int?,
        localOptedIn: Boolean,
        localStatus: SubscriptionStatus,
        localAddress: String,
    ): Triple<RefreshUserOperationExecutor, SubscriptionModel, IUserBackendService> {
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId) } returns
            CreateUserResponse(
                mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId),
                PropertiesObject(),
                listOf(
                    SubscriptionObject(
                        existingSubscriptionId1,
                        SubscriptionObjectType.ANDROID_PUSH,
                        enabled = serverPushEnabled,
                        notificationTypes = serverNotificationTypes,
                        token = "on-backend-push-token",
                    ),
                ),
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

        val cachedPushSubscriptionModel = SubscriptionModel()
        cachedPushSubscriptionModel.id = existingSubscriptionId1
        cachedPushSubscriptionModel.type = SubscriptionType.PUSH
        cachedPushSubscriptionModel.address = localAddress
        cachedPushSubscriptionModel.status = localStatus
        cachedPushSubscriptionModel.optedIn = localOptedIn
        every { mockSubscriptionsModelStore.get(existingSubscriptionId1) } returns cachedPushSubscriptionModel

        val mockConfigModelStore =
            MockHelper.configModelStore {
                it.pushSubscriptionId = existingSubscriptionId1
            }

        val executor =
            RefreshUserOperationExecutor(
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                mockConfigModelStore,
                mockk<IRebuildUserService>(),
                getNewRecordState(),
                getJwtTokenStore(), getIdentityVerificationService(),
            )

        return Triple(executor, cachedPushSubscriptionModel, mockUserBackendService)
    }

    test("push self-heal: enqueues follow-up update-subscription op when server is stuck-disabled but local is enabled") {
        // Given: server view says push is disabled (the stuck state), local view says enabled
        val (executor, _, _) =
            buildSelfHealHarness(
                serverPushEnabled = false,
                serverNotificationTypes = 0,
                localOptedIn = true,
                localStatus = SubscriptionStatus.SUBSCRIBED,
                localAddress = onDevicePushToken,
            )

        // Suppress logcat output for the self-heal WARN line so the unmocked android.util.Log
        // in robolectric-free unit tests doesn't blow up. Restored in finally.
        val originalLogLevel = Logging.logLevel
        Logging.logLevel = LogLevel.NONE
        try {
            // When
            val response = executor.execute(listOf(RefreshUserOperation(appId, remoteOneSignalId, null)))

            // Then a single UpdateSubscriptionOperation is returned to the op repo
            response.result shouldBe ExecutionResult.SUCCESS
            response.operations?.count() shouldBe 1
            val followup = response.operations!![0]
            (followup is UpdateSubscriptionOperation) shouldBe true
            followup as UpdateSubscriptionOperation
            followup.name shouldBe SubscriptionOperationExecutor.UPDATE_SUBSCRIPTION
            followup.appId shouldBe appId
            followup.onesignalId shouldBe remoteOneSignalId
            followup.subscriptionId shouldBe existingSubscriptionId1
            followup.type shouldBe SubscriptionType.PUSH
            followup.enabled shouldBe true
            followup.address shouldBe onDevicePushToken
            followup.status shouldBe SubscriptionStatus.SUBSCRIBED
        } finally {
            Logging.logLevel = originalLogLevel
        }
    }

    test("push self-heal: does NOT enqueue follow-up op when server matches local (healthy push subscription)") {
        // Given: server already enabled and notificationTypes=1, local also enabled
        val (executor, _, _) =
            buildSelfHealHarness(
                serverPushEnabled = true,
                serverNotificationTypes = 1,
                localOptedIn = true,
                localStatus = SubscriptionStatus.SUBSCRIBED,
                localAddress = onDevicePushToken,
            )

        // When
        val response = executor.execute(listOf(RefreshUserOperation(appId, remoteOneSignalId, null)))

        // Then no follow-up ops emitted
        response.result shouldBe ExecutionResult.SUCCESS
        response.operations shouldBe null
    }

    test("push self-heal: does NOT enqueue follow-up op when local is opted out (UNSUBSCRIBE is intentional)") {
        // Given: server is disabled, but so is local (user explicitly opted out)
        val (executor, _, _) =
            buildSelfHealHarness(
                serverPushEnabled = false,
                serverNotificationTypes = -2,
                localOptedIn = false,
                localStatus = SubscriptionStatus.SUBSCRIBED,
                localAddress = onDevicePushToken,
            )

        // When
        val response = executor.execute(listOf(RefreshUserOperation(appId, remoteOneSignalId, null)))

        // Then no follow-up — opt-out is the user's intent, not divergence
        response.result shouldBe ExecutionResult.SUCCESS
        response.operations shouldBe null
    }

    test("push self-heal: does NOT enqueue follow-up op when local has NO_PERMISSION (OS-level disable is real)") {
        // Given: server disabled, local also has no notification permission
        val (executor, _, _) =
            buildSelfHealHarness(
                serverPushEnabled = false,
                serverNotificationTypes = 0,
                localOptedIn = true,
                localStatus = SubscriptionStatus.NO_PERMISSION,
                localAddress = onDevicePushToken,
            )

        // When
        val response = executor.execute(listOf(RefreshUserOperation(appId, remoteOneSignalId, null)))

        // Then no follow-up — OS denies notifications, server's view is correct
        response.result shouldBe ExecutionResult.SUCCESS
        response.operations shouldBe null
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

        val refreshUserOperationExecutor =
            RefreshUserOperationExecutor(
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                MockHelper.configModelStore(),
                mockBuildUserService,
                newRecordState,
                getJwtTokenStore(), getIdentityVerificationService(),
            )

        val operations = listOf<Operation>(RefreshUserOperation(appId, remoteOneSignalId, null))

        // When
        val response = refreshUserOperationExecutor.execute(operations)

        // Then
        response.result shouldBe ExecutionResult.FAIL_RETRY
        coVerify(exactly = 1) {
            mockUserBackendService.getUser(appId, IdentityConstants.ONESIGNAL_ID, remoteOneSignalId)
        }
    }
})
