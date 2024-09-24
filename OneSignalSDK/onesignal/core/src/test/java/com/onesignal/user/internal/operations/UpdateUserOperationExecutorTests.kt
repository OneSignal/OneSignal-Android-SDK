package com.onesignal.user.internal.operations

import com.onesignal.common.consistency.enums.IamFetchRywTokenKey
import com.onesignal.common.consistency.models.IConsistencyManager
import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.Operation
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.backend.IUserBackendService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.builduser.IRebuildUserService
import com.onesignal.user.internal.operations.ExecutorMocks.Companion.getNewRecordState
import com.onesignal.user.internal.operations.impl.executors.UpdateUserOperationExecutor
import com.onesignal.user.internal.properties.PropertiesModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import java.math.BigDecimal

class UpdateUserOperationExecutorTests :
    FunSpec({
        val appId = "appId"
        val localOneSignalId = "local-onesignalId"
        val remoteOneSignalId = "remote-onesignalId"
        val rywToken = "1"
        val mockConsistencyManager = mockk<IConsistencyManager>()

        beforeTest {
            clearMocks(mockConsistencyManager)
            coEvery { mockConsistencyManager.setRywToken(any(), any(), any()) } just runs
        }

        test("update user single operation is successful") {
            // Given
            val mockUserBackendService = mockk<IUserBackendService>()
            coEvery { mockUserBackendService.updateUser(any(), any(), any(), any(), any(), any()) } returns rywToken

            // Given
            val mockIdentityModelStore = MockHelper.identityModelStore()
            val mockPropertiesModelStore = MockHelper.propertiesModelStore()
            val mockBuildUserService = mockk<IRebuildUserService>()

            val loginUserOperationExecutor =
                UpdateUserOperationExecutor(
                    mockUserBackendService,
                    mockIdentityModelStore,
                    mockPropertiesModelStore,
                    mockBuildUserService,
                    getNewRecordState(),
                    mockConsistencyManager,
                )
            val operations = listOf<Operation>(SetTagOperation(appId, remoteOneSignalId, "tagKey1", "tagValue1"))

            // When
            val response = loginUserOperationExecutor.execute(operations)

            // Then
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
                    any(),
                )
            }
        }

        test("update user multiple property operations are successful") {
            // Given
            val mockUserBackendService = mockk<IUserBackendService>()
            coEvery { mockUserBackendService.updateUser(any(), any(), any(), any(), any(), any()) } returns rywToken

            // Given
            val mockIdentityModelStore = MockHelper.identityModelStore()
            val mockPropertiesModelStore = MockHelper.propertiesModelStore()
            val mockBuildUserService = mockk<IRebuildUserService>()

            val loginUserOperationExecutor =
                UpdateUserOperationExecutor(
                    mockUserBackendService,
                    mockIdentityModelStore,
                    mockPropertiesModelStore,
                    mockBuildUserService,
                    getNewRecordState(),
                    mockConsistencyManager,
                )
            val operations =
                listOf<Operation>(
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
                    SetPropertyOperation(appId, localOneSignalId, PropertiesModel::locationTimestamp.name, 1111L),
                )

            // When
            val response = loginUserOperationExecutor.execute(operations)

            // Then
            response.result shouldBe ExecutionResult.SUCCESS
            coVerify(exactly = 1) {
                mockUserBackendService.updateUser(
                    appId,
                    IdentityConstants.ONESIGNAL_ID,
                    remoteOneSignalId,
                    withArg {
                        it.tags shouldBe mapOf("tagKey1" to "tagValue1-2", "tagKey2" to "tagValue2", "tagKey3" to null)
                        it.country shouldBe "country"
                        it.language shouldBe "lang2"
                        it.timezoneId shouldBe "timezone"
                        it.latitude shouldBe 123.45
                        it.longitude shouldBe 678.90
                    },
                    any(),
                    any(),
                )
            }
        }

        test("update user single property delta operations is successful") {
            // Given
            val mockUserBackendService = mockk<IUserBackendService>()
            coEvery { mockUserBackendService.updateUser(any(), any(), any(), any(), any(), any()) } returns rywToken

            // Given
            val mockIdentityModelStore = MockHelper.identityModelStore()
            val mockPropertiesModelStore = MockHelper.propertiesModelStore()
            val mockBuildUserService = mockk<IRebuildUserService>()

            val loginUserOperationExecutor =
                UpdateUserOperationExecutor(
                    mockUserBackendService,
                    mockIdentityModelStore,
                    mockPropertiesModelStore,
                    mockBuildUserService,
                    getNewRecordState(),
                    mockConsistencyManager,
                )
            val operations =
                listOf<Operation>(
                    TrackSessionEndOperation(appId, remoteOneSignalId, 1111),
                )

            // When
            val response = loginUserOperationExecutor.execute(operations)

            // Then
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
                    },
                )
            }
        }

        test("update user multiple property delta operations are successful") {
            // Given
            val mockUserBackendService = mockk<IUserBackendService>()
            coEvery { mockUserBackendService.updateUser(any(), any(), any(), any(), any(), any()) } returns rywToken

            // Given
            val mockIdentityModelStore = MockHelper.identityModelStore()
            val mockPropertiesModelStore = MockHelper.propertiesModelStore()
            val mockBuildUserService = mockk<IRebuildUserService>()

            val loginUserOperationExecutor =
                UpdateUserOperationExecutor(
                    mockUserBackendService,
                    mockIdentityModelStore,
                    mockPropertiesModelStore,
                    mockBuildUserService,
                    getNewRecordState(),
                    mockConsistencyManager,
                )
            val operations =
                listOf<Operation>(
                    TrackSessionEndOperation(appId, remoteOneSignalId, 1111),
                    TrackPurchaseOperation(
                        appId,
                        remoteOneSignalId,
                        false,
                        BigDecimal(2222),
                        listOf(
                            PurchaseInfo("sku1", "iso1", BigDecimal(1000)),
                            PurchaseInfo("sku2", "iso2", BigDecimal(1222)),
                        ),
                    ),
                    TrackSessionEndOperation(appId, remoteOneSignalId, 3333),
                )

            // When
            val response = loginUserOperationExecutor.execute(operations)

            // Then
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
                    },
                )
            }
        }

        test("update user with both property and property delta operations are successful") {
            // Given
            val mockUserBackendService = mockk<IUserBackendService>()
            coEvery { mockUserBackendService.updateUser(any(), any(), any(), any(), any(), any()) } returns rywToken

            // Given
            val mockIdentityModelStore = MockHelper.identityModelStore()
            val mockPropertiesModelStore = MockHelper.propertiesModelStore()
            val mockBuildUserService = mockk<IRebuildUserService>()

            val loginUserOperationExecutor =
                UpdateUserOperationExecutor(
                    mockUserBackendService,
                    mockIdentityModelStore,
                    mockPropertiesModelStore,
                    mockBuildUserService,
                    getNewRecordState(),
                    mockConsistencyManager,
                )
            val operations =
                listOf<Operation>(
                    TrackSessionEndOperation(appId, remoteOneSignalId, 1111),
                    SetTagOperation(appId, remoteOneSignalId, "tagKey1", "tagValue1"),
                    TrackSessionEndOperation(appId, remoteOneSignalId, 3333),
                )

            // When
            val response = loginUserOperationExecutor.execute(operations)

            // Then
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
                    },
                )
            }
        }

        test("update user single operation fails with MISSING") {
            // Given
            val mockUserBackendService = mockk<IUserBackendService>()
            coEvery { mockUserBackendService.updateUser(any(), any(), any(), any(), any(), any()) } throws BackendException(404)

            // Given
            val mockIdentityModelStore = MockHelper.identityModelStore()
            val mockPropertiesModelStore = MockHelper.propertiesModelStore()
            val mockBuildUserService = mockk<IRebuildUserService>()
            every { mockBuildUserService.getRebuildOperationsIfCurrentUser(any(), any()) } returns null

            val loginUserOperationExecutor =
                UpdateUserOperationExecutor(
                    mockUserBackendService,
                    mockIdentityModelStore,
                    mockPropertiesModelStore,
                    mockBuildUserService,
                    getNewRecordState(),
                    mockConsistencyManager,
                )
            val operations = listOf(SetTagOperation(appId, remoteOneSignalId, "tagKey1", "tagValue1"))

            // When
            val response = loginUserOperationExecutor.execute(operations)

            // Then
            response.result shouldBe ExecutionResult.FAIL_NORETRY
        }

        test("update user single operation fails with MISSING, but isInMissingRetryWindow") {
            // Given
            val mockUserBackendService = mockk<IUserBackendService>()
            coEvery { mockUserBackendService.updateUser(any(), any(), any(), any(), any(), any()) } throws
                BackendException(404, retryAfterSeconds = 10)

            // Given
            val mockIdentityModelStore = MockHelper.identityModelStore()
            val mockPropertiesModelStore = MockHelper.propertiesModelStore()
            val mockBuildUserService = mockk<IRebuildUserService>()
            every { mockBuildUserService.getRebuildOperationsIfCurrentUser(any(), any()) } returns null

            val mockConfigModelStore = MockHelper.configModelStore().also { it.model.opRepoPostCreateRetryUpTo = 1_000 }
            val newRecordState = getNewRecordState(mockConfigModelStore).also { it.add(remoteOneSignalId) }

            val loginUserOperationExecutor =
                UpdateUserOperationExecutor(
                    mockUserBackendService,
                    mockIdentityModelStore,
                    mockPropertiesModelStore,
                    mockBuildUserService,
                    newRecordState,
                    mockConsistencyManager,
                )
            val operations = listOf(SetTagOperation(appId, remoteOneSignalId, "tagKey1", "tagValue1"))

            // When
            val response = loginUserOperationExecutor.execute(operations)

            // Then
            response.result shouldBe ExecutionResult.FAIL_RETRY
            response.retryAfterSeconds shouldBe 10
        }

        test("setRywToken is called after successful user update of session count") {
            // Given
            val mockUserBackendService = mockk<IUserBackendService>()
            coEvery {
                mockUserBackendService.updateUser(any(), any(), any(), any(), any(), any())
            } returns rywToken

            val mockIdentityModelStore = MockHelper.identityModelStore()
            val mockPropertiesModelStore = MockHelper.propertiesModelStore()
            val mockBuildUserService = mockk<IRebuildUserService>()

            val loginUserOperationExecutor =
                UpdateUserOperationExecutor(
                    mockUserBackendService,
                    mockIdentityModelStore,
                    mockPropertiesModelStore,
                    mockBuildUserService,
                    getNewRecordState(),
                    mockConsistencyManager,
                )

            val operations =
                listOf<Operation>(
                    TrackSessionStartOperation(appId, onesignalId = remoteOneSignalId),
                )

            // When
            loginUserOperationExecutor.execute(operations)

            // Then
            coVerify(exactly = 1) {
                mockConsistencyManager.setRywToken(remoteOneSignalId, IamFetchRywTokenKey.USER, rywToken)
            }
        }
    })
