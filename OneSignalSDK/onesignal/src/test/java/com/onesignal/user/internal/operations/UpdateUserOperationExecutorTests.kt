package com.onesignal.user.internal.operations

import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.Operation
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.backend.IUserBackendService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.operations.impl.executors.UpdateUserOperationExecutor
import com.onesignal.user.internal.properties.PropertiesModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.runner.junit4.KotestTestRunner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(KotestTestRunner::class)
class UpdateUserOperationExecutorTests : FunSpec({
    val appId = "appId"
    val localOneSignalId = "local-onesignalId"
    val remoteOneSignalId = "remote-onesignalId"

    test("update user single operation is successful") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.updateUser(any(), any(), any(), any(), any(), any()) } just runs

        /* Given */
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()

        val loginUserOperationExecutor = UpdateUserOperationExecutor(
            mockUserBackendService,
            mockIdentityModelStore,
            mockPropertiesModelStore
        )
        val operations = listOf<Operation>(SetTagOperation(appId, remoteOneSignalId, "tagKey1", "tagValue1"))

        /* When */
        val response = loginUserOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.SUCCESS
        coVerify(exactly = 1) {
            mockUserBackendService.updateUser(
                appId,
                IdentityConstants.ONESIGNAL_ID,
                remoteOneSignalId,
                withArg {
                    it.tags shouldBe mapOf("tagKey1" to "tagValue1")
                },
                any(),
                any()
            )
        }
    }

    test("update user multiple property operations are successful") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.updateUser(any(), any(), any(), any(), any(), any()) } just runs

        /* Given */
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()

        val loginUserOperationExecutor = UpdateUserOperationExecutor(
            mockUserBackendService,
            mockIdentityModelStore,
            mockPropertiesModelStore
        )
        val operations = listOf<Operation>(
            SetTagOperation(appId, remoteOneSignalId, "tagKey1", "tagValue1-1"),
            SetTagOperation(appId, remoteOneSignalId, "tagKey1", "tagValue1-2"),
            SetTagOperation(appId, remoteOneSignalId, "tagKey2", "tagValue2"),
            SetTagOperation(appId, remoteOneSignalId, "tagKey3", "tagValue3"),
            DeleteTagOperation(appId, remoteOneSignalId, "tagKey3"),
            SetPropertyOperation(appId, localOneSignalId, PropertiesModel::language.name, "lang1"),
            SetPropertyOperation(appId, localOneSignalId, PropertiesModel::language.name, "lang2"),
            SetPropertyOperation(appId, localOneSignalId, PropertiesModel::timezone.name, "timezone"),
            SetPropertyOperation(appId, localOneSignalId, PropertiesModel::country.name, "country"),
            SetPropertyOperation(appId, localOneSignalId, PropertiesModel::locationLatitude.name, 123.45),
            SetPropertyOperation(appId, localOneSignalId, PropertiesModel::locationLongitude.name, 678.90),
            SetPropertyOperation(appId, localOneSignalId, PropertiesModel::locationType.name, 1),
            SetPropertyOperation(appId, localOneSignalId, PropertiesModel::locationAccuracy.name, 0.15),
            SetPropertyOperation(appId, localOneSignalId, PropertiesModel::locationBackground.name, true),
            SetPropertyOperation(appId, localOneSignalId, PropertiesModel::locationTimestamp.name, 1111L)
        )

        /* When */
        val response = loginUserOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.SUCCESS
        coVerify(exactly = 1) {
            mockUserBackendService.updateUser(
                appId,
                IdentityConstants.ONESIGNAL_ID,
                remoteOneSignalId,
                withArg {
                    it.tags shouldBe mapOf("tagKey1" to "tagValue1-2", "tagKey2" to "tagValue2")
                    it.country shouldBe "country"
                    it.language shouldBe "lang2"
                    it.timezoneId shouldBe "timezone"
                    it.latitude shouldBe 123.45
                    it.longitude shouldBe 678.90
                },
                any(),
                any()
            )
        }
    }

    test("update user single property delta operations is successful") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.updateUser(any(), any(), any(), any(), any(), any()) } just runs

        /* Given */
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()

        val loginUserOperationExecutor = UpdateUserOperationExecutor(
            mockUserBackendService,
            mockIdentityModelStore,
            mockPropertiesModelStore
        )
        val operations = listOf<Operation>(
            TrackSessionEndOperation(appId, remoteOneSignalId, 1111)
        )

        /* When */
        val response = loginUserOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.SUCCESS
        coVerify(exactly = 1) {
            mockUserBackendService.updateUser(
                appId,
                IdentityConstants.ONESIGNAL_ID,
                remoteOneSignalId,
                withArg {
                    it.tags shouldBe null
                },
                any(),
                withArg {
                    it.sessionTime shouldBe 1111
                }
            )
        }
    }

    test("update user multiple property delta operations are successful") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.updateUser(any(), any(), any(), any(), any(), any()) } just runs

        /* Given */
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()

        val loginUserOperationExecutor = UpdateUserOperationExecutor(
            mockUserBackendService,
            mockIdentityModelStore,
            mockPropertiesModelStore
        )
        val operations = listOf<Operation>(
            TrackSessionEndOperation(appId, remoteOneSignalId, 1111),
            TrackPurchaseOperation(
                appId,
                remoteOneSignalId,
                false,
                BigDecimal(2222),
                listOf(
                    PurchaseInfo("sku1", "iso1", BigDecimal(1000)),
                    PurchaseInfo("sku2", "iso2", BigDecimal(1222))
                )
            ),
            TrackSessionEndOperation(appId, remoteOneSignalId, 3333)
        )

        /* When */
        val response = loginUserOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.SUCCESS
        coVerify(exactly = 1) {
            mockUserBackendService.updateUser(
                appId,
                IdentityConstants.ONESIGNAL_ID,
                remoteOneSignalId,
                withArg {
                    it.tags shouldBe null
                },
                any(),
                withArg {
                    it.sessionTime shouldBe (1111 + 3333)
                    it.amountSpent shouldBe BigDecimal(2222)
                    it.purchases shouldNotBe null
                    it.purchases!!.count() shouldBe 2
                    it.purchases!![0].sku shouldBe "sku1"
                    it.purchases!![0].iso shouldBe "iso1"
                    it.purchases!![0].amount shouldBe BigDecimal(1000)
                    it.purchases!![1].sku shouldBe "sku2"
                    it.purchases!![1].iso shouldBe "iso2"
                    it.purchases!![1].amount shouldBe BigDecimal(1222)
                }
            )
        }
    }

    test("update user with both property and property delta operations are successful") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.updateUser(any(), any(), any(), any(), any(), any()) } just runs

        /* Given */
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()

        val loginUserOperationExecutor = UpdateUserOperationExecutor(
            mockUserBackendService,
            mockIdentityModelStore,
            mockPropertiesModelStore
        )
        val operations = listOf<Operation>(
            TrackSessionEndOperation(appId, remoteOneSignalId, 1111),
            SetTagOperation(appId, remoteOneSignalId, "tagKey1", "tagValue1"),
            TrackSessionEndOperation(appId, remoteOneSignalId, 3333)
        )

        /* When */
        val response = loginUserOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.SUCCESS
        coVerify(exactly = 1) {
            mockUserBackendService.updateUser(
                appId,
                IdentityConstants.ONESIGNAL_ID,
                remoteOneSignalId,
                withArg {
                    it.tags shouldBe mapOf("tagKey1" to "tagValue1")
                },
                any(),
                withArg {
                    it.sessionTime shouldBe (1111 + 3333)
                }
            )
        }
    }
})
