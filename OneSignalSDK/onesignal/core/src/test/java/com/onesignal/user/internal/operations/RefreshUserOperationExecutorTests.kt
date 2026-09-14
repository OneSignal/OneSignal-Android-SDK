package com.onesignal.user.internal.operations

import com.onesignal.common.TimeUtils
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
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
import com.onesignal.user.internal.operations.impl.listeners.SubscriptionModelStoreListener
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

    /**
     * The same fetch as [buildSelfHealHarness], but against a real store with the real model store
     * listener attached, so a test sees the operations a hydration actually produces rather than
     * only the model it leaves behind.
     */
    fun buildCorrectiveUpdateHarness(
        serverPushEnabled: Boolean,
        serverNotificationTypes: Int?,
        localOptedIn: Boolean,
        localRemoteDisabledReason: Int,
    ): Triple<RefreshUserOperationExecutor, SubscriptionModel, MutableList<Operation>> {
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
        val identityModel = IdentityModel()
        identityModel.onesignalId = remoteOneSignalId
        every { mockIdentityModelStore.model } returns identityModel
        every { mockIdentityModelStore.replace(any(), any()) } just runs

        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val propertiesModel = PropertiesModel()
        propertiesModel.onesignalId = remoteOneSignalId
        every { mockPropertiesModelStore.model } returns propertiesModel
        every { mockPropertiesModelStore.replace(any(), any()) } just runs

        val subscriptionModelStore = SubscriptionModelStore(MockPreferencesService())
        val cachedPushSubscriptionModel =
            SubscriptionModel().apply {
                id = existingSubscriptionId1
                type = SubscriptionType.PUSH
                address = onDevicePushToken
                status = SubscriptionStatus.SUBSCRIBED
                optedIn = localOptedIn
                remoteDisabledReason = localRemoteDisabledReason
            }
        // NO_PROPOGATE so seeding the store does not enqueue a create.
        subscriptionModelStore.add(cachedPushSubscriptionModel, ModelChangeTags.NO_PROPOGATE)

        val enqueued = mutableListOf<Operation>()
        val mockOpRepo = mockk<IOperationRepo>(relaxed = true)
        every { mockOpRepo.enqueue(capture(enqueued), any()) } just runs

        val configModelStore = MockHelper.configModelStore { it.pushSubscriptionId = existingSubscriptionId1 }

        SubscriptionModelStoreListener(
            subscriptionModelStore,
            mockOpRepo,
            mockIdentityModelStore,
            configModelStore,
        ).bootstrap()

        val executor =
            RefreshUserOperationExecutor(
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                subscriptionModelStore,
                configModelStore,
                mockk<IRebuildUserService>(),
                getNewRecordState(),
                getJwtTokenStore(), getIdentityVerificationService(),
            )

        return Triple(executor, cachedPushSubscriptionModel, enqueued)
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

    // Both remote-disable codes mean "the app owner turned this off", so both suppress the
    // self-heal, and each is recorded verbatim so the payload echoes back the code the server sent
    // rather than a single collapsed one.
    listOf(
        SubscriptionStatus.MANUALLY_UNSUBSCRIBED,
        SubscriptionStatus.DISABLED_FROM_REST_API,
    ).forEach { remoteDisable ->
        test("push self-heal: does NOT enqueue follow-up op when the server reports ${remoteDisable.value}") {
            // Given: server says push is disabled with a remote-disable code, local view says enabled
            val (executor, cachedPushSubscriptionModel, _) =
                buildSelfHealHarness(
                    serverPushEnabled = false,
                    serverNotificationTypes = remoteDisable.value,
                    localOptedIn = true,
                    localStatus = SubscriptionStatus.SUBSCRIBED,
                    localAddress = onDevicePushToken,
                )

            // When
            val response = executor.execute(listOf(RefreshUserOperation(appId, remoteOneSignalId, null)))

            // Then no follow-up op, and that exact code is recorded on the cached push model
            response.result shouldBe ExecutionResult.SUCCESS
            response.operations shouldBe null
            cachedPushSubscriptionModel.remoteDisabledReason shouldBe remoteDisable.value
        }
    }

    test("push self-heal: still re-asserts local truth when the server reports another disabled code") {
        // Any disabled code other than the remote-disable codes (-22, -31) stays device-recoverable
        val (executor, cachedPushSubscriptionModel, _) =
            buildSelfHealHarness(
                serverPushEnabled = false,
                serverNotificationTypes = -2,
                localOptedIn = true,
                localStatus = SubscriptionStatus.SUBSCRIBED,
                localAddress = onDevicePushToken,
            )

        val originalLogLevel = Logging.logLevel
        Logging.logLevel = LogLevel.NONE
        try {
            // When
            val response = executor.execute(listOf(RefreshUserOperation(appId, remoteOneSignalId, null)))

            // Then the self-heal op is emitted and nothing is recorded as a remote disable
            response.result shouldBe ExecutionResult.SUCCESS
            response.operations?.count() shouldBe 1
            (response.operations!![0] is UpdateSubscriptionOperation) shouldBe true
            cachedPushSubscriptionModel.remoteDisabledReason shouldBe 0
        } finally {
            Logging.logLevel = originalLogLevel
        }
    }

    test("push refresh: clears a recorded remote disable when the server reports another code") {
        // Given: -31 recorded locally, server now reports a different code
        val (executor, cachedPushSubscriptionModel, _) =
            buildSelfHealHarness(
                serverPushEnabled = false,
                serverNotificationTypes = -2,
                localOptedIn = true,
                localStatus = SubscriptionStatus.SUBSCRIBED,
                localAddress = onDevicePushToken,
            )
        cachedPushSubscriptionModel.remoteDisabledReason = SubscriptionStatus.DISABLED_FROM_REST_API.value

        val originalLogLevel = Logging.logLevel
        Logging.logLevel = LogLevel.NONE
        try {
            // When
            val response = executor.execute(listOf(RefreshUserOperation(appId, remoteOneSignalId, null)))

            // Then the mirror clears and the self-heal still re-asserts local truth
            response.result shouldBe ExecutionResult.SUCCESS
            cachedPushSubscriptionModel.remoteDisabledReason shouldBe 0
            response.operations?.count() shouldBe 1
        } finally {
            Logging.logLevel = originalLogLevel
        }
    }

    test("push refresh: clears a recorded remote disable when the server reports enabled again") {
        // Given: a locally recorded remote disable, server now reports the subscription enabled
        val (executor, cachedPushSubscriptionModel, _) =
            buildSelfHealHarness(
                serverPushEnabled = true,
                serverNotificationTypes = 1,
                localOptedIn = true,
                localStatus = SubscriptionStatus.SUBSCRIBED,
                localAddress = onDevicePushToken,
            )
        cachedPushSubscriptionModel.remoteDisabledReason = SubscriptionStatus.DISABLED_FROM_REST_API.value
        cachedPushSubscriptionModel.remoteDisableClearedByUser = true

        // When
        val response = executor.execute(listOf(RefreshUserOperation(appId, remoteOneSignalId, null)))

        // Then the mirror clears and the opt-in's precedence over stale reports ends
        response.result shouldBe ExecutionResult.SUCCESS
        response.operations shouldBe null
        cachedPushSubscriptionModel.remoteDisabledReason shouldBe 0
        cachedPushSubscriptionModel.remoteDisableClearedByUser shouldBe false
    }

    test("push refresh: keeps an opt-in over a fetch that still reports the remote disable it cleared") {
        // Given: optIn() ran while this fetch was pending, so the server still reports -31
        val (executor, cachedPushSubscriptionModel, _) =
            buildSelfHealHarness(
                serverPushEnabled = false,
                serverNotificationTypes = SubscriptionStatus.DISABLED_FROM_REST_API.value,
                localOptedIn = true,
                localStatus = SubscriptionStatus.SUBSCRIBED,
                localAddress = onDevicePushToken,
            )
        cachedPushSubscriptionModel.remoteDisableClearedByUser = true

        // When
        val response = executor.execute(listOf(RefreshUserOperation(appId, remoteOneSignalId, null)))

        // Then the stale disable is not recorded, the flag stays, and no self-heal fires
        response.result shouldBe ExecutionResult.SUCCESS
        response.operations shouldBe null
        cachedPushSubscriptionModel.remoteDisabledReason shouldBe 0
        cachedPushSubscriptionModel.remoteDisableClearedByUser shouldBe true
    }

    test("push refresh: recording a remote disable enqueues the update that re-applies it") {
        // A device-metadata update queued earlier in the session carries the enabled it was built
        // with, from before the disable was known. On its own it re-enables the subscription, and
        // the next fetch then reports it as enabled and clears the local record, so neither the
        // device nor the server is left holding the disable. The update this hydration produces is
        // what replaces the stale one, or puts the state back if it already went out.
        val (executor, cachedPushSubscriptionModel, enqueued) =
            buildCorrectiveUpdateHarness(
                serverPushEnabled = false,
                serverNotificationTypes = SubscriptionStatus.MANUALLY_UNSUBSCRIBED.value,
                localOptedIn = true,
                localRemoteDisabledReason = 0,
            )

        // When
        val response = executor.execute(listOf(RefreshUserOperation(appId, remoteOneSignalId, null)))

        // Then
        response.result shouldBe ExecutionResult.SUCCESS
        cachedPushSubscriptionModel.remoteDisabledReason shouldBe SubscriptionStatus.MANUALLY_UNSUBSCRIBED.value
        val corrective = enqueued.filterIsInstance<UpdateSubscriptionOperation>().last()
        corrective.subscriptionId shouldBe existingSubscriptionId1
        corrective.enabled shouldBe false
        corrective.status shouldBe SubscriptionStatus.MANUALLY_UNSUBSCRIBED
    }

    test("push refresh: clearing a remote disable sends the opt-out the device could not send") {
        // While a disable is recorded every payload reports it, so an opt-out made during the
        // suppression never reaches the server. Clearing the record is the first chance to send it.
        val (executor, cachedPushSubscriptionModel, enqueued) =
            buildCorrectiveUpdateHarness(
                serverPushEnabled = true,
                serverNotificationTypes = 1,
                localOptedIn = false,
                localRemoteDisabledReason = SubscriptionStatus.DISABLED_FROM_REST_API.value,
            )

        // When
        val response = executor.execute(listOf(RefreshUserOperation(appId, remoteOneSignalId, null)))

        // Then
        response.result shouldBe ExecutionResult.SUCCESS
        cachedPushSubscriptionModel.remoteDisabledReason shouldBe 0
        val corrective = enqueued.filterIsInstance<UpdateSubscriptionOperation>().last()
        corrective.enabled shouldBe false
        corrective.status shouldBe SubscriptionStatus.UNSUBSCRIBE
    }
})
