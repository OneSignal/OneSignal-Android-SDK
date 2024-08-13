package com.onesignal.user.internal.operations

import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.consistency.enums.IamFetchRywTokenKey
import com.onesignal.common.consistency.models.IConsistencyManager
import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.Operation
import com.onesignal.mocks.AndroidMockHelper
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.backend.ISubscriptionBackendService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.backend.SubscriptionObjectType
import com.onesignal.user.internal.builduser.IRebuildUserService
import com.onesignal.user.internal.operations.ExecutorMocks.Companion.getNewRecordState
import com.onesignal.user.internal.operations.impl.executors.SubscriptionOperationExecutor
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.internal.subscriptions.SubscriptionType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

@RobolectricTest
class SubscriptionOperationExecutorTests :
    FunSpec({
        val appId = "appId"
        val remoteOneSignalId = "remote-onesignalId"
        val localSubscriptionId = "local-subscriptionId1"
        val remoteSubscriptionId = "remote-subscriptionId1"
        val rywToken = "1"
        val mockConsistencyManager = mockk<IConsistencyManager>()

        beforeTest {
            clearMocks(mockConsistencyManager)
            coEvery { mockConsistencyManager.setRywToken(any(), any(), any()) } just runs
        }

        test("create subscription successfully creates subscription") {
            // Given
            val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
            coEvery { mockSubscriptionBackendService.createSubscription(any(), any(), any(), any()) } returns
                Pair(remoteSubscriptionId, rywToken)

            val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
            val mockIdentityModelStore = MockHelper.identityModelStore()
            val subscriptionModel1 = SubscriptionModel()
            subscriptionModel1.id = localSubscriptionId
            every { mockSubscriptionsModelStore.get(localSubscriptionId) } returns subscriptionModel1

            val mockBuildUserService = mockk<IRebuildUserService>()

            val subscriptionOperationExecutor =
                SubscriptionOperationExecutor(
                    mockSubscriptionBackendService,
                    MockHelper.deviceService(),
                    AndroidMockHelper.applicationService(),
                    mockIdentityModelStore,
                    mockSubscriptionsModelStore,
                    MockHelper.configModelStore(),
                    mockBuildUserService,
                    getNewRecordState(),
                    mockConsistencyManager,
                )

            val operations =
                listOf<Operation>(
                    CreateSubscriptionOperation(
                        appId,
                        remoteOneSignalId,
                        localSubscriptionId,
                        SubscriptionType.PUSH,
                        true,
                        "pushToken",
                        SubscriptionStatus.SUBSCRIBED,
                    ),
                )

            // When
            val response = subscriptionOperationExecutor.execute(operations)

            // Then
            response.result shouldBe ExecutionResult.SUCCESS
            subscriptionModel1.id shouldBe remoteSubscriptionId
            coVerify(exactly = 1) {
                mockSubscriptionBackendService.createSubscription(
                    appId,
                    IdentityConstants.ONESIGNAL_ID,
                    remoteOneSignalId,
                    withArg {
                        it.type shouldBe SubscriptionObjectType.ANDROID_PUSH
                        it.enabled shouldBe true
                        it.token shouldBe "pushToken"
                        it.notificationTypes shouldBe SubscriptionStatus.SUBSCRIBED.value
                    },
                )
            }
        }

        test("create subscription fails with retry when there is a network condition") {
            // Given
            val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
            coEvery { mockSubscriptionBackendService.createSubscription(any(), any(), any(), any()) } throws
                BackendException(408, retryAfterSeconds = 10)

            val mockIdentityModelStore = MockHelper.identityModelStore()
            val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
            val mockBuildUserService = mockk<IRebuildUserService>()

            val subscriptionOperationExecutor =
                SubscriptionOperationExecutor(
                    mockSubscriptionBackendService,
                    MockHelper.deviceService(),
                    AndroidMockHelper.applicationService(),
                    mockIdentityModelStore,
                    mockSubscriptionsModelStore,
                    MockHelper.configModelStore(),
                    mockBuildUserService,
                    getNewRecordState(),
                    mockConsistencyManager,
                )

            val operations =
                listOf<Operation>(
                    CreateSubscriptionOperation(
                        appId,
                        remoteOneSignalId,
                        localSubscriptionId,
                        SubscriptionType.PUSH,
                        true,
                        "pushToken",
                        SubscriptionStatus.SUBSCRIBED,
                    ),
                )

            // When
            val response = subscriptionOperationExecutor.execute(operations)

            // Then
            response.result shouldBe ExecutionResult.FAIL_RETRY
            response.retryAfterSeconds shouldBe 10
            coVerify(exactly = 1) {
                mockSubscriptionBackendService.createSubscription(
                    appId,
                    IdentityConstants.ONESIGNAL_ID,
                    remoteOneSignalId,
                    withArg {
                        it.type shouldBe SubscriptionObjectType.ANDROID_PUSH
                        it.enabled shouldBe true
                        it.token shouldBe "pushToken"
                        it.notificationTypes shouldBe SubscriptionStatus.SUBSCRIBED.value
                    },
                )
            }
        }

        test("create subscription fails without retry when there is a backend error") {
            // Given
            val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
            coEvery { mockSubscriptionBackendService.createSubscription(any(), any(), any(), any()) } throws BackendException(404)

            val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
            val mockBuildUserService = mockk<IRebuildUserService>()
            every { mockBuildUserService.getRebuildOperationsIfCurrentUser(any(), any()) } answers { null }

            val subscriptionOperationExecutor =
                SubscriptionOperationExecutor(
                    mockSubscriptionBackendService,
                    MockHelper.deviceService(),
                    AndroidMockHelper.applicationService(),
                    MockHelper.identityModelStore(),
                    mockSubscriptionsModelStore,
                    MockHelper.configModelStore(),
                    mockBuildUserService,
                    getNewRecordState(),
                    mockConsistencyManager,
                )

            val operations =
                listOf<Operation>(
                    CreateSubscriptionOperation(
                        appId,
                        remoteOneSignalId,
                        localSubscriptionId,
                        SubscriptionType.PUSH,
                        true,
                        "pushToken",
                        SubscriptionStatus.SUBSCRIBED,
                    ),
                )

            // When
            val response = subscriptionOperationExecutor.execute(operations)

            // Then
            response.result shouldBe ExecutionResult.FAIL_NORETRY
            coVerify(exactly = 1) {
                mockSubscriptionBackendService.createSubscription(
                    appId,
                    IdentityConstants.ONESIGNAL_ID,
                    remoteOneSignalId,
                    withArg {
                        it.type shouldBe SubscriptionObjectType.ANDROID_PUSH
                        it.enabled shouldBe true
                        it.token shouldBe "pushToken"
                        it.notificationTypes shouldBe SubscriptionStatus.SUBSCRIBED.value
                    },
                )
            }
        }

        test("create subscription fails with retry when the backend returns MISSING, when isInMissingRetryWindow") {
            // Given
            val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
            coEvery { mockSubscriptionBackendService.createSubscription(any(), any(), any(), any()) } throws BackendException(404)

            val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
            val mockBuildUserService = mockk<IRebuildUserService>()
            val mockIdentityModelStore = MockHelper.identityModelStore()
            val mockConfigModelStore = MockHelper.configModelStore().also { it.model.opRepoPostCreateRetryUpTo = 1_000 }
            val newRecordState = getNewRecordState(mockConfigModelStore).also { it.add(remoteOneSignalId) }
            val subscriptionModel1 = SubscriptionModel()
            subscriptionModel1.id = remoteSubscriptionId
            subscriptionModel1.address = "pushToken1"
            every { mockSubscriptionsModelStore.get(remoteSubscriptionId) } returns subscriptionModel1

            val subscriptionOperationExecutor =
                SubscriptionOperationExecutor(
                    mockSubscriptionBackendService,
                    MockHelper.deviceService(),
                    AndroidMockHelper.applicationService(),
                    mockIdentityModelStore,
                    mockSubscriptionsModelStore,
                    MockHelper.configModelStore(),
                    mockBuildUserService,
                    newRecordState,
                    mockConsistencyManager,
                )

            val operations =
                listOf<Operation>(
                    CreateSubscriptionOperation(
                        appId,
                        remoteOneSignalId,
                        localSubscriptionId,
                        SubscriptionType.PUSH,
                        true,
                        "pushToken",
                        SubscriptionStatus.SUBSCRIBED,
                    ),
                )

            // When
            val response = subscriptionOperationExecutor.execute(operations)

            // Then
            response.result shouldBe ExecutionResult.FAIL_RETRY
        }

        test("create subscription then delete subscription is a successful no-op") {
            // Given
            val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
            val mockIdentityModelStore = MockHelper.identityModelStore()
            val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
            val subscriptionModel1 = SubscriptionModel()
            subscriptionModel1.id = localSubscriptionId
            every { mockSubscriptionsModelStore.get(localSubscriptionId) } returns subscriptionModel1

            val mockBuildUserService = mockk<IRebuildUserService>()

            val subscriptionOperationExecutor =
                SubscriptionOperationExecutor(
                    mockSubscriptionBackendService,
                    MockHelper.deviceService(),
                    AndroidMockHelper.applicationService(),
                    mockIdentityModelStore,
                    mockSubscriptionsModelStore,
                    MockHelper.configModelStore(),
                    mockBuildUserService,
                    getNewRecordState(),
                    mockConsistencyManager,
                )

            val operations =
                listOf<Operation>(
                    CreateSubscriptionOperation(
                        appId,
                        remoteOneSignalId,
                        localSubscriptionId,
                        SubscriptionType.PUSH,
                        true,
                        "pushToken",
                        SubscriptionStatus.SUBSCRIBED,
                    ),
                    DeleteSubscriptionOperation(appId, remoteOneSignalId, localSubscriptionId),
                )

            // When
            val response = subscriptionOperationExecutor.execute(operations)

            // Then
            response.result shouldBe ExecutionResult.SUCCESS
        }

        test("create subscription then update subscription successfully creates subscription") {
            // Given
            val mockIdentityModelStore = MockHelper.identityModelStore()
            val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
            coEvery { mockSubscriptionBackendService.createSubscription(any(), any(), any(), any()) } returns
                Pair(remoteSubscriptionId, rywToken)

            val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
            val subscriptionModel1 = SubscriptionModel()
            subscriptionModel1.id = localSubscriptionId
            every { mockSubscriptionsModelStore.get(localSubscriptionId) } returns subscriptionModel1

            val mockBuildUserService = mockk<IRebuildUserService>()

            val subscriptionOperationExecutor =
                SubscriptionOperationExecutor(
                    mockSubscriptionBackendService,
                    MockHelper.deviceService(),
                    AndroidMockHelper.applicationService(),
                    mockIdentityModelStore,
                    mockSubscriptionsModelStore,
                    MockHelper.configModelStore(),
                    mockBuildUserService,
                    getNewRecordState(),
                    mockConsistencyManager,
                )

            val operations =
                listOf<Operation>(
                    CreateSubscriptionOperation(
                        appId,
                        remoteOneSignalId,
                        localSubscriptionId,
                        SubscriptionType.PUSH,
                        true,
                        "pushToken1",
                        SubscriptionStatus.SUBSCRIBED,
                    ),
                    UpdateSubscriptionOperation(
                        appId,
                        remoteOneSignalId,
                        localSubscriptionId,
                        SubscriptionType.PUSH,
                        true,
                        "pushToken2",
                        SubscriptionStatus.SUBSCRIBED,
                    ),
                )

            // When
            val response = subscriptionOperationExecutor.execute(operations)

            // Then
            response.result shouldBe ExecutionResult.SUCCESS
            subscriptionModel1.id shouldBe remoteSubscriptionId
            coVerify(exactly = 1) {
                mockSubscriptionBackendService.createSubscription(
                    appId,
                    IdentityConstants.ONESIGNAL_ID,
                    remoteOneSignalId,
                    withArg {
                        it.type shouldBe SubscriptionObjectType.ANDROID_PUSH
                        it.enabled shouldBe true
                        it.token shouldBe "pushToken2"
                        it.notificationTypes shouldBe SubscriptionStatus.SUBSCRIBED.value
                    },
                )
            }
        }

        test("update subscription successfully updates subscription") {
            // Given
            val mockIdentityModelStore = MockHelper.identityModelStore()
            val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
            coEvery { mockSubscriptionBackendService.updateSubscription(any(), any(), any()) } returns rywToken

            val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
            val subscriptionModel1 =
                SubscriptionModel().apply {
                    id = remoteSubscriptionId
                    address = "pushToken1"
                }
            every { mockSubscriptionsModelStore.get(remoteSubscriptionId) } returns subscriptionModel1

            val mockBuildUserService = mockk<IRebuildUserService>()

            val subscriptionOperationExecutor =
                SubscriptionOperationExecutor(
                    mockSubscriptionBackendService,
                    MockHelper.deviceService(),
                    AndroidMockHelper.applicationService(),
                    mockIdentityModelStore,
                    mockSubscriptionsModelStore,
                    MockHelper.configModelStore(),
                    mockBuildUserService,
                    getNewRecordState(),
                    mockConsistencyManager,
                )

            val operations =
                listOf(
                    UpdateSubscriptionOperation(
                        appId,
                        remoteOneSignalId,
                        remoteSubscriptionId,
                        SubscriptionType.PUSH,
                        true,
                        "pushToken2",
                        SubscriptionStatus.SUBSCRIBED,
                    ),
                    UpdateSubscriptionOperation(
                        appId,
                        remoteOneSignalId,
                        remoteSubscriptionId,
                        SubscriptionType.PUSH,
                        true,
                        "pushToken3",
                        SubscriptionStatus.SUBSCRIBED,
                    ),
                )

            // When
            val response = subscriptionOperationExecutor.execute(operations)

            // Then
            response.result shouldBe ExecutionResult.SUCCESS
            coVerify(exactly = 1) {
                mockSubscriptionBackendService.updateSubscription(
                    appId,
                    remoteSubscriptionId,
                    withArg {
                        it.type shouldBe SubscriptionObjectType.ANDROID_PUSH
                        it.enabled shouldBe true
                        it.token shouldBe "pushToken3"
                        it.notificationTypes shouldBe SubscriptionStatus.SUBSCRIBED.value
                    },
                )
            }
        }

        test("update subscription fails with retry when there is a network condition") {
            // Given
            val mockIdentityModelStore = MockHelper.identityModelStore()
            val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
            coEvery { mockSubscriptionBackendService.updateSubscription(any(), any(), any()) } throws BackendException(408)

            val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
            val mockBuildUserService = mockk<IRebuildUserService>()

            val subscriptionOperationExecutor =
                SubscriptionOperationExecutor(
                    mockSubscriptionBackendService,
                    MockHelper.deviceService(),
                    AndroidMockHelper.applicationService(),
                    mockIdentityModelStore,
                    mockSubscriptionsModelStore,
                    MockHelper.configModelStore(),
                    mockBuildUserService,
                    getNewRecordState(),
                    mockConsistencyManager,
                )

            val operations =
                listOf<Operation>(
                    UpdateSubscriptionOperation(
                        appId,
                        remoteOneSignalId,
                        remoteSubscriptionId,
                        SubscriptionType.PUSH,
                        true,
                        "pushToken2",
                        SubscriptionStatus.SUBSCRIBED,
                    ),
                )

            // When
            val response = subscriptionOperationExecutor.execute(operations)

            // Then
            response.result shouldBe ExecutionResult.FAIL_RETRY
            coVerify(exactly = 1) {
                mockSubscriptionBackendService.updateSubscription(
                    appId,
                    remoteSubscriptionId,
                    withArg {
                        it.type shouldBe SubscriptionObjectType.ANDROID_PUSH
                        it.enabled shouldBe true
                        it.token shouldBe "pushToken2"
                        it.notificationTypes shouldBe SubscriptionStatus.SUBSCRIBED.value
                    },
                )
            }
        }

        test("update subscription fails without retry when there is a backend error") {
            // Given
            val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
            coEvery { mockSubscriptionBackendService.updateSubscription(any(), any(), any()) } throws BackendException(404)

            val mockIdentityModelStore = MockHelper.identityModelStore()
            val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
            val mockBuildUserService = mockk<IRebuildUserService>()

            val subscriptionOperationExecutor =
                SubscriptionOperationExecutor(
                    mockSubscriptionBackendService,
                    MockHelper.deviceService(),
                    AndroidMockHelper.applicationService(),
                    mockIdentityModelStore,
                    mockSubscriptionsModelStore,
                    MockHelper.configModelStore(),
                    mockBuildUserService,
                    getNewRecordState(),
                    mockConsistencyManager,
                )

            val operations =
                listOf<Operation>(
                    UpdateSubscriptionOperation(
                        appId,
                        remoteOneSignalId,
                        remoteSubscriptionId,
                        SubscriptionType.PUSH,
                        true,
                        "pushToken2",
                        SubscriptionStatus.SUBSCRIBED,
                    ),
                )

            // When
            val response = subscriptionOperationExecutor.execute(operations)

            // Then
            response.result shouldBe ExecutionResult.FAIL_NORETRY
            coVerify(exactly = 1) {
                mockSubscriptionBackendService.updateSubscription(
                    appId,
                    remoteSubscriptionId,
                    withArg {
                        it.type shouldBe SubscriptionObjectType.ANDROID_PUSH
                        it.enabled shouldBe true
                        it.token shouldBe "pushToken2"
                        it.notificationTypes shouldBe SubscriptionStatus.SUBSCRIBED.value
                    },
                )
            }
        }

        test("update subscription fails with retry when the backend returns MISSING, when isInMissingRetryWindow") {
            // Given
            val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
            coEvery { mockSubscriptionBackendService.updateSubscription(any(), any(), any()) } throws BackendException(404)

            val mockIdentityModelStore = MockHelper.identityModelStore()
            val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
            val mockBuildUserService = mockk<IRebuildUserService>()
            val mockConfigModelStore = MockHelper.configModelStore().also { it.model.opRepoPostCreateRetryUpTo = 1_000 }
            val newRecordState = getNewRecordState(mockConfigModelStore).also { it.add(remoteOneSignalId) }

            val subscriptionOperationExecutor =
                SubscriptionOperationExecutor(
                    mockSubscriptionBackendService,
                    MockHelper.deviceService(),
                    AndroidMockHelper.applicationService(),
                    mockIdentityModelStore,
                    mockSubscriptionsModelStore,
                    MockHelper.configModelStore(),
                    mockBuildUserService,
                    newRecordState,
                    mockConsistencyManager,
                )

            val operations =
                listOf<Operation>(
                    UpdateSubscriptionOperation(
                        appId,
                        remoteOneSignalId,
                        remoteSubscriptionId,
                        SubscriptionType.PUSH,
                        true,
                        "pushToken2",
                        SubscriptionStatus.SUBSCRIBED,
                    ),
                )

            // When
            val response = subscriptionOperationExecutor.execute(operations)

            // Then
            response.result shouldBe ExecutionResult.FAIL_RETRY
        }

        test("delete subscription successfully deletes subscription") {
            // Given
            val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
            coEvery { mockSubscriptionBackendService.deleteSubscription(any(), any()) } just runs

            val mockIdentityModelStore = MockHelper.identityModelStore()
            val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
            every { mockSubscriptionsModelStore.remove(any(), any()) } just runs

            val mockBuildUserService = mockk<IRebuildUserService>()

            val subscriptionOperationExecutor =
                SubscriptionOperationExecutor(
                    mockSubscriptionBackendService,
                    MockHelper.deviceService(),
                    AndroidMockHelper.applicationService(),
                    mockIdentityModelStore,
                    mockSubscriptionsModelStore,
                    MockHelper.configModelStore(),
                    mockBuildUserService,
                    getNewRecordState(),
                    mockConsistencyManager,
                )

            val operations =
                listOf<Operation>(
                    DeleteSubscriptionOperation(appId, remoteOneSignalId, remoteSubscriptionId),
                )

            // When
            val response = subscriptionOperationExecutor.execute(operations)

            // Then
            response.result shouldBe ExecutionResult.SUCCESS
            coVerify(exactly = 1) { mockSubscriptionBackendService.deleteSubscription(appId, remoteSubscriptionId) }
            verify(exactly = 1) { mockSubscriptionsModelStore.remove(remoteSubscriptionId, any()) }
        }

        test("delete subscription fails with retry when there is a network condition") {
            // Given
            val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
            coEvery { mockSubscriptionBackendService.deleteSubscription(any(), any()) } throws BackendException(408)

            val mockIdentityModelStore = MockHelper.identityModelStore()
            val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
            val mockBuildUserService = mockk<IRebuildUserService>()

            val subscriptionOperationExecutor =
                SubscriptionOperationExecutor(
                    mockSubscriptionBackendService,
                    MockHelper.deviceService(),
                    AndroidMockHelper.applicationService(),
                    mockIdentityModelStore,
                    mockSubscriptionsModelStore,
                    MockHelper.configModelStore(),
                    mockBuildUserService,
                    getNewRecordState(),
                    mockConsistencyManager,
                )

            val operations =
                listOf<Operation>(
                    DeleteSubscriptionOperation(appId, remoteOneSignalId, remoteSubscriptionId),
                )

            // When
            val response = subscriptionOperationExecutor.execute(operations)

            // Then
            response.result shouldBe ExecutionResult.FAIL_RETRY
            coVerify(exactly = 1) { mockSubscriptionBackendService.deleteSubscription(appId, remoteSubscriptionId) }
        }

        // If we get a 404 then the subscription has already been deleted,
        // so we count it as successful
        test("delete subscription is successful if there is a 404") {
            // Given
            val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
            coEvery { mockSubscriptionBackendService.deleteSubscription(any(), any()) } throws BackendException(404)

            val mockIdentityModelStore = MockHelper.identityModelStore()
            val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
            val mockBuildUserService = mockk<IRebuildUserService>()

            val subscriptionOperationExecutor =
                SubscriptionOperationExecutor(
                    mockSubscriptionBackendService,
                    MockHelper.deviceService(),
                    AndroidMockHelper.applicationService(),
                    mockIdentityModelStore,
                    mockSubscriptionsModelStore,
                    MockHelper.configModelStore(),
                    mockBuildUserService,
                    getNewRecordState(),
                    mockConsistencyManager,
                )

            val operations =
                listOf<Operation>(
                    DeleteSubscriptionOperation(appId, remoteOneSignalId, remoteSubscriptionId),
                )

            // When
            val response = subscriptionOperationExecutor.execute(operations)

            // Then
            response.result shouldBe ExecutionResult.SUCCESS
            coVerify(exactly = 1) { mockSubscriptionBackendService.deleteSubscription(appId, remoteSubscriptionId) }
        }

        test("delete subscription fails with retry when the backend returns MISSING, when isInMissingRetryWindow") {
            // Given
            val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
            coEvery { mockSubscriptionBackendService.deleteSubscription(any(), any()) } throws BackendException(404)

            val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
            val mockBuildUserService = mockk<IRebuildUserService>()
            val mockConfigModelStore = MockHelper.configModelStore().also { it.model.opRepoPostCreateRetryUpTo = 1_000 }
            val newRecordState = getNewRecordState(mockConfigModelStore).also { it.add(remoteOneSignalId) }

            val subscriptionOperationExecutor =
                SubscriptionOperationExecutor(
                    mockSubscriptionBackendService,
                    MockHelper.deviceService(),
                    AndroidMockHelper.applicationService(),
                    MockHelper.identityModelStore(),
                    mockSubscriptionsModelStore,
                    MockHelper.configModelStore(),
                    mockBuildUserService,
                    newRecordState,
                    mockConsistencyManager,
                )

            val operations =
                listOf<Operation>(
                    DeleteSubscriptionOperation(appId, remoteOneSignalId, remoteSubscriptionId),
                )

            // When
            val response = subscriptionOperationExecutor.execute(operations)

            // Then
            response.result shouldBe ExecutionResult.FAIL_RETRY
        }

        test("setRywToken is called after successful subscription update") {
            // Given
            val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
            coEvery {
                mockSubscriptionBackendService.updateSubscription(any(), any(), any())
            } returns rywToken

            val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
            val subscriptionModel1 =
                SubscriptionModel().apply {
                    id = remoteSubscriptionId
                    address = "pushToken1"
                }
            every { mockSubscriptionsModelStore.get(remoteSubscriptionId) } returns subscriptionModel1

            val mockBuildUserService = mockk<IRebuildUserService>()

            val subscriptionOperationExecutor =
                SubscriptionOperationExecutor(
                    mockSubscriptionBackendService,
                    MockHelper.deviceService(),
                    AndroidMockHelper.applicationService(),
                    MockHelper.identityModelStore(),
                    mockSubscriptionsModelStore,
                    MockHelper.configModelStore(),
                    mockBuildUserService,
                    getNewRecordState(),
                    mockConsistencyManager,
                )

            val operations =
                listOf(
                    UpdateSubscriptionOperation(
                        appId,
                        remoteOneSignalId,
                        remoteSubscriptionId,
                        SubscriptionType.PUSH,
                        true,
                        "pushToken2",
                        SubscriptionStatus.SUBSCRIBED,
                    ),
                )

            subscriptionOperationExecutor.execute(operations)

            // Then
            coVerify(exactly = 1) {
                mockConsistencyManager.setRywToken(remoteOneSignalId, IamFetchRywTokenKey.SUBSCRIPTION, rywToken)
            }
        }
    })
