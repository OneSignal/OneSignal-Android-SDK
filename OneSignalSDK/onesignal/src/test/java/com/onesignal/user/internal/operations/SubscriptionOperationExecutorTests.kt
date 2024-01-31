package com.onesignal.user.internal.operations

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.Operation
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.backend.ISubscriptionBackendService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.backend.SubscriptionObjectType
import com.onesignal.user.internal.operations.impl.executors.SubscriptionOperationExecutor
import com.onesignal.user.internal.subscriptions.SubscriptionModel
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
import io.mockk.verify
import org.junit.runner.RunWith

@RunWith(KotestTestRunner::class)
class SubscriptionOperationExecutorTests : FunSpec({
    val appId = "appId"
    val remoteOneSignalId = "remote-onesignalId"
    val localSubscriptionId = "local-subscriptionId1"
    val remoteSubscriptionId = "remote-subscriptionId1"

    test("create subscription successfully creates subscription") {
        /* Given */
        val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
        coEvery { mockSubscriptionBackendService.createSubscription(any(), any(), any(), any(), any(), any(), any()) } returns remoteSubscriptionId

        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        val subscriptionModel1 = SubscriptionModel()
        subscriptionModel1.id = localSubscriptionId
        every { mockSubscriptionsModelStore.get(localSubscriptionId) } returns subscriptionModel1
        val subscriptionOperationExecutor = SubscriptionOperationExecutor(
            mockSubscriptionBackendService,
            MockHelper.deviceService(),
            mockSubscriptionsModelStore
        )

        val operations = listOf<Operation>(CreateSubscriptionOperation(appId, remoteOneSignalId, localSubscriptionId, SubscriptionType.PUSH, true, "pushToken", SubscriptionModel.STATUS_SUBSCRIBED))

        /* When */
        val response = subscriptionOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.SUCCESS
        subscriptionModel1.id shouldBe remoteSubscriptionId
        coVerify(exactly = 1) {
            mockSubscriptionBackendService.createSubscription(
                appId,
                IdentityConstants.ONESIGNAL_ID,
                remoteOneSignalId,
                SubscriptionObjectType.ANDROID_PUSH,
                true,
                "pushToken",
                SubscriptionModel.STATUS_SUBSCRIBED
            )
        }
    }

    test("create subscription fails with retry when there is a network condition") {
        /* Given */
        val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
        coEvery { mockSubscriptionBackendService.createSubscription(any(), any(), any(), any(), any(), any(), any()) } throws BackendException(408)

        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        val subscriptionOperationExecutor = SubscriptionOperationExecutor(
            mockSubscriptionBackendService,
            MockHelper.deviceService(),
            mockSubscriptionsModelStore
        )

        val operations = listOf<Operation>(CreateSubscriptionOperation(appId, remoteOneSignalId, localSubscriptionId, SubscriptionType.PUSH, true, "pushToken", SubscriptionModel.STATUS_SUBSCRIBED))

        /* When */
        val response = subscriptionOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.FAIL_RETRY
        coVerify(exactly = 1) {
            mockSubscriptionBackendService.createSubscription(
                appId,
                IdentityConstants.ONESIGNAL_ID,
                remoteOneSignalId,
                SubscriptionObjectType.ANDROID_PUSH,
                true,
                "pushToken",
                SubscriptionModel.STATUS_SUBSCRIBED
            )
        }
    }

    test("create subscription fails without retry when there is a backend error") {
        /* Given */
        val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
        coEvery { mockSubscriptionBackendService.createSubscription(any(), any(), any(), any(), any(), any(), any()) } throws BackendException(404)

        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        val subscriptionOperationExecutor = SubscriptionOperationExecutor(
            mockSubscriptionBackendService,
            MockHelper.deviceService(),
            mockSubscriptionsModelStore
        )

        val operations = listOf<Operation>(CreateSubscriptionOperation(appId, remoteOneSignalId, localSubscriptionId, SubscriptionType.PUSH, true, "pushToken", SubscriptionModel.STATUS_SUBSCRIBED))

        /* When */
        val response = subscriptionOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.FAIL_NORETRY
        coVerify(exactly = 1) {
            mockSubscriptionBackendService.createSubscription(
                appId,
                IdentityConstants.ONESIGNAL_ID,
                remoteOneSignalId,
                SubscriptionObjectType.ANDROID_PUSH,
                true,
                "pushToken",
                SubscriptionModel.STATUS_SUBSCRIBED
            )
        }
    }

    test("create subscription then delete subscription is a successful no-op") {
        /* Given */
        val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()

        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        val subscriptionModel1 = SubscriptionModel()
        subscriptionModel1.id = localSubscriptionId
        every { mockSubscriptionsModelStore.get(localSubscriptionId) } returns subscriptionModel1
        val subscriptionOperationExecutor = SubscriptionOperationExecutor(
            mockSubscriptionBackendService,
            MockHelper.deviceService(),
            mockSubscriptionsModelStore
        )

        val operations = listOf<Operation>(
            CreateSubscriptionOperation(appId, remoteOneSignalId, localSubscriptionId, SubscriptionType.PUSH, true, "pushToken", SubscriptionModel.STATUS_SUBSCRIBED),
            DeleteSubscriptionOperation(appId, remoteOneSignalId, localSubscriptionId)
        )

        /* When */
        val response = subscriptionOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.SUCCESS
    }

    test("create subscription then update subscription successfully creates subscription") {
        /* Given */
        val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
        coEvery { mockSubscriptionBackendService.createSubscription(any(), any(), any(), any(), any(), any(), any()) } returns remoteSubscriptionId

        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        val subscriptionModel1 = SubscriptionModel()
        subscriptionModel1.id = localSubscriptionId
        every { mockSubscriptionsModelStore.get(localSubscriptionId) } returns subscriptionModel1
        val subscriptionOperationExecutor = SubscriptionOperationExecutor(
            mockSubscriptionBackendService,
            MockHelper.deviceService(),
            mockSubscriptionsModelStore
        )

        val operations = listOf<Operation>(
            CreateSubscriptionOperation(appId, remoteOneSignalId, localSubscriptionId, SubscriptionType.PUSH, true, "pushToken1", SubscriptionModel.STATUS_SUBSCRIBED),
            UpdateSubscriptionOperation(appId, remoteOneSignalId, localSubscriptionId, SubscriptionType.PUSH, true, "pushToken2", SubscriptionModel.STATUS_SUBSCRIBED)
        )

        /* When */
        val response = subscriptionOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.SUCCESS
        subscriptionModel1.id shouldBe remoteSubscriptionId
        coVerify(exactly = 1) {
            mockSubscriptionBackendService.createSubscription(
                appId,
                IdentityConstants.ONESIGNAL_ID,
                remoteOneSignalId,
                SubscriptionObjectType.ANDROID_PUSH,
                true,
                "pushToken2",
                SubscriptionModel.STATUS_SUBSCRIBED
            )
        }
    }

    test("update subscription successfully updates subscription") {
        /* Given */
        val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
        coEvery { mockSubscriptionBackendService.updateSubscription(any(), any(), any(), any(), any()) } just runs

        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        val subscriptionModel1 = SubscriptionModel()
        subscriptionModel1.id = remoteSubscriptionId
        subscriptionModel1.address = "pushToken1"
        every { mockSubscriptionsModelStore.get(remoteSubscriptionId) } returns subscriptionModel1

        val subscriptionOperationExecutor = SubscriptionOperationExecutor(
            mockSubscriptionBackendService,
            MockHelper.deviceService(),
            mockSubscriptionsModelStore
        )

        val operations = listOf<Operation>(
            UpdateSubscriptionOperation(appId, remoteOneSignalId, remoteSubscriptionId, SubscriptionType.PUSH, true, "pushToken2", SubscriptionModel.STATUS_SUBSCRIBED),
            UpdateSubscriptionOperation(appId, remoteOneSignalId, remoteSubscriptionId, SubscriptionType.PUSH, true, "pushToken3", SubscriptionModel.STATUS_SUBSCRIBED)
        )

        /* When */
        val response = subscriptionOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.SUCCESS
        subscriptionModel1.address shouldBe "pushToken3"
        coVerify(exactly = 1) {
            mockSubscriptionBackendService.updateSubscription(
                appId,
                remoteSubscriptionId,
                true,
                "pushToken3",
                SubscriptionModel.STATUS_SUBSCRIBED
            )
        }
    }

    test("update subscription fails with retry when there is a network condition") {
        /* Given */
        val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
        coEvery { mockSubscriptionBackendService.updateSubscription(any(), any(), any(), any(), any()) } throws BackendException(408)

        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        val subscriptionOperationExecutor = SubscriptionOperationExecutor(
            mockSubscriptionBackendService,
            MockHelper.deviceService(),
            mockSubscriptionsModelStore
        )

        val operations = listOf<Operation>(
            UpdateSubscriptionOperation(appId, remoteOneSignalId, remoteSubscriptionId, SubscriptionType.PUSH, true, "pushToken2", SubscriptionModel.STATUS_SUBSCRIBED)
        )

        /* When */
        val response = subscriptionOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.FAIL_RETRY
        coVerify(exactly = 1) {
            mockSubscriptionBackendService.updateSubscription(
                appId,
                remoteSubscriptionId,
                true,
                "pushToken2",
                SubscriptionModel.STATUS_SUBSCRIBED
            )
        }
    }

    test("update subscription fails without retry when there is a backend error") {
        /* Given */
        val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
        coEvery { mockSubscriptionBackendService.updateSubscription(any(), any(), any(), any(), any()) } throws BackendException(404)

        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        val subscriptionOperationExecutor = SubscriptionOperationExecutor(
            mockSubscriptionBackendService,
            MockHelper.deviceService(),
            mockSubscriptionsModelStore
        )

        val operations = listOf<Operation>(
            UpdateSubscriptionOperation(appId, remoteOneSignalId, remoteSubscriptionId, SubscriptionType.PUSH, true, "pushToken2", SubscriptionModel.STATUS_SUBSCRIBED)
        )

        /* When */
        val response = subscriptionOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.FAIL_NORETRY
        coVerify(exactly = 1) {
            mockSubscriptionBackendService.updateSubscription(
                appId,
                remoteSubscriptionId,
                true,
                "pushToken2",
                SubscriptionModel.STATUS_SUBSCRIBED
            )
        }
    }

    test("delete subscription successfully deletes subscription") {
        /* Given */
        val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
        coEvery { mockSubscriptionBackendService.deleteSubscription(any(), any()) } just runs

        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        every { mockSubscriptionsModelStore.remove(any(), any()) } just runs

        val subscriptionOperationExecutor = SubscriptionOperationExecutor(
            mockSubscriptionBackendService,
            MockHelper.deviceService(),
            mockSubscriptionsModelStore
        )

        val operations = listOf<Operation>(
            UpdateSubscriptionOperation(appId, remoteOneSignalId, remoteSubscriptionId, SubscriptionType.PUSH, true, "pushToken2", SubscriptionModel.STATUS_SUBSCRIBED),
            DeleteSubscriptionOperation(appId, remoteOneSignalId, remoteSubscriptionId)
        )

        /* When */
        val response = subscriptionOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.SUCCESS
        coVerify(exactly = 1) { mockSubscriptionBackendService.deleteSubscription(appId, remoteSubscriptionId) }
        verify(exactly = 1) { mockSubscriptionsModelStore.remove(remoteSubscriptionId, any()) }
    }

    test("delete subscription fails with retry when there is a network condition") {
        /* Given */
        val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
        coEvery { mockSubscriptionBackendService.deleteSubscription(any(), any()) } throws BackendException(408)

        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        val subscriptionOperationExecutor = SubscriptionOperationExecutor(
            mockSubscriptionBackendService,
            MockHelper.deviceService(),
            mockSubscriptionsModelStore
        )

        val operations = listOf<Operation>(
            DeleteSubscriptionOperation(appId, remoteOneSignalId, remoteSubscriptionId)
        )

        /* When */
        val response = subscriptionOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.FAIL_RETRY
        coVerify(exactly = 1) { mockSubscriptionBackendService.deleteSubscription(appId, remoteSubscriptionId) }
    }

    test("delete subscription fails without retry when there is a backend error") {
        /* Given */
        val mockSubscriptionBackendService = mockk<ISubscriptionBackendService>()
        coEvery { mockSubscriptionBackendService.deleteSubscription(any(), any()) } throws BackendException(404)

        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        val subscriptionOperationExecutor = SubscriptionOperationExecutor(
            mockSubscriptionBackendService,
            MockHelper.deviceService(),
            mockSubscriptionsModelStore
        )

        val operations = listOf<Operation>(
            DeleteSubscriptionOperation(appId, remoteOneSignalId, remoteSubscriptionId)
        )

        /* When */
        val response = subscriptionOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.FAIL_NORETRY
        coVerify(exactly = 1) { mockSubscriptionBackendService.deleteSubscription(appId, remoteSubscriptionId) }
    }
})
