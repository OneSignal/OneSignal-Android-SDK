package com.onesignal.user.internal.operations

import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.Operation
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.backend.IIdentityBackendService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.impl.executors.IdentityOperationExecutor
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
class IdentityOperationExecutorTests : FunSpec({

    test("execution of set alias operation") {
        /* Given */
        val mockIdentityBackendService = mockk<IIdentityBackendService>()
        coEvery { mockIdentityBackendService.createAlias(any(), any(), any(), any()) } returns mapOf()

        val mockIdentityModel = mockk<IdentityModel>()
        every { mockIdentityModel.onesignalId } returns "onesignalId"
        every { mockIdentityModel.setProperty<String>(any(), any(), any()) } just runs

        val mockIdentityModelStore = mockk<IdentityModelStore>()
        every { mockIdentityModelStore.model } returns mockIdentityModel

        val identityOperationExecutor = IdentityOperationExecutor(mockIdentityBackendService, mockIdentityModelStore)
        val operations = listOf<Operation>(SetAliasOperation("appId", "onesignalId", "aliasKey1", "aliasValue1"))

        /* When */
        val response = identityOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.SUCCESS
        coVerify(exactly = 1) { mockIdentityBackendService.createAlias("appId", IdentityConstants.ONESIGNAL_ID, "onesignalId", mapOf("aliasKey1" to "aliasValue1")) }
        verify(exactly = 1) { mockIdentityModel.setProperty("aliasKey1", "aliasValue1", ModelChangeTags.HYDRATE) }
    }

    test("execution of set alias operation with network timeout") {
        /* Given */
        val mockIdentityBackendService = mockk<IIdentityBackendService>()
        coEvery { mockIdentityBackendService.createAlias(any(), any(), any(), any()) } throws BackendException(408, "TIMEOUT")

        val mockIdentityModelStore = MockHelper.identityModelStore()

        val identityOperationExecutor = IdentityOperationExecutor(mockIdentityBackendService, mockIdentityModelStore)
        val operations = listOf<Operation>(SetAliasOperation("appId", "onesignalId", "aliasKey1", "aliasValue1"))

        /* When */

        val response = identityOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.FAIL_RETRY
    }

    test("execution of set alias operation with non-retryable error") {
        /* Given */
        val mockIdentityBackendService = mockk<IIdentityBackendService>()
        coEvery { mockIdentityBackendService.createAlias(any(), any(), any(), any()) } throws BackendException(404, "NOT FOUND")

        val mockIdentityModelStore = MockHelper.identityModelStore()

        val identityOperationExecutor = IdentityOperationExecutor(mockIdentityBackendService, mockIdentityModelStore)
        val operations = listOf<Operation>(SetAliasOperation("appId", "onesignalId", "aliasKey1", "aliasValue1"))

        /* When */

        val response = identityOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.FAIL_NORETRY
    }

    test("execution of delete alias operation") {
        /* Given */
        val mockIdentityBackendService = mockk<IIdentityBackendService>()
        coEvery { mockIdentityBackendService.deleteAlias(any(), any(), any(), any()) } just runs

        val mockIdentityModel = mockk<IdentityModel>()
        every { mockIdentityModel.onesignalId } returns "onesignalId"
        every { mockIdentityModel.setProperty<String>(any(), any(), any()) } just runs

        val mockIdentityModelStore = mockk<IdentityModelStore>()
        every { mockIdentityModelStore.model } returns mockIdentityModel

        val identityOperationExecutor = IdentityOperationExecutor(mockIdentityBackendService, mockIdentityModelStore)
        val operations = listOf<Operation>(DeleteAliasOperation("appId", "onesignalId", "aliasKey1"))

        /* When */
        val response = identityOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.SUCCESS
        coVerify(exactly = 1) { mockIdentityBackendService.deleteAlias("appId", IdentityConstants.ONESIGNAL_ID, "onesignalId", "aliasKey1") }
        verify(exactly = 1) { mockIdentityModel.setProperty("aliasKey1", null, ModelChangeTags.HYDRATE) }
    }

    test("execution of delete alias operation with network timeout") {
        /* Given */
        val mockIdentityBackendService = mockk<IIdentityBackendService>()
        coEvery { mockIdentityBackendService.deleteAlias(any(), any(), any(), any()) } throws BackendException(408, "TIMEOUT")

        val mockIdentityModelStore = MockHelper.identityModelStore()

        val identityOperationExecutor = IdentityOperationExecutor(mockIdentityBackendService, mockIdentityModelStore)
        val operations = listOf<Operation>(DeleteAliasOperation("appId", "onesignalId", "aliasKey1"))

        /* When */

        val response = identityOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.FAIL_RETRY
    }

    test("execution of delete alias operation with non-retryable error") {
        /* Given */
        val mockIdentityBackendService = mockk<IIdentityBackendService>()
        coEvery { mockIdentityBackendService.deleteAlias(any(), any(), any(), any()) } throws BackendException(404, "NOT FOUND")

        val mockIdentityModelStore = MockHelper.identityModelStore()

        val identityOperationExecutor = IdentityOperationExecutor(mockIdentityBackendService, mockIdentityModelStore)
        val operations = listOf<Operation>(DeleteAliasOperation("appId", "onesignalId", "aliasKey1"))

        /* When */

        val response = identityOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.FAIL_NORETRY
    }
})
