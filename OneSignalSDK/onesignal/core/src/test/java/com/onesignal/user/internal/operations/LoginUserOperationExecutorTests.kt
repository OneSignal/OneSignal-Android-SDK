package com.onesignal.user.internal.operations

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.Operation
import com.onesignal.extensions.RobolectricTest
import com.onesignal.mocks.AndroidMockHelper
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.backend.CreateUserResponse
import com.onesignal.user.internal.backend.IUserBackendService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.backend.PropertiesObject
import com.onesignal.user.internal.backend.SubscriptionObject
import com.onesignal.user.internal.backend.SubscriptionObjectType
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.operations.impl.executors.IdentityOperationExecutor
import com.onesignal.user.internal.operations.impl.executors.LoginUserOperationExecutor
import com.onesignal.user.internal.properties.PropertiesModel
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.internal.subscriptions.SubscriptionType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeOneOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.runner.junit4.KotestTestRunner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.runner.RunWith

@RobolectricTest
@RunWith(KotestTestRunner::class)
class LoginUserOperationExecutorTests : FunSpec({
    val appId = "appId"
    val localOneSignalId = "local-onesignalId"
    val remoteOneSignalId = "remote-onesignalId"
    val localSubscriptionId1 = "local-subscriptionId1"
    val localSubscriptionId2 = "local-subscriptionId2"
    val remoteSubscriptionId1 = "remote-subscriptionId1"
    val remoteSubscriptionId2 = "remote-subscriptionId2"

    test("login anonymous user successfully creates user") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } returns
            CreateUserResponse(
                mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId),
                PropertiesObject(),
                listOf()
            )
        /* Given */
        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor = LoginUserOperationExecutor(
            mockIdentityOperationExecutor,
            MockHelper.applicationService(),
            MockHelper.deviceService(),
            mockUserBackendService,
            mockIdentityModelStore,
            mockPropertiesModelStore,
            mockSubscriptionsModelStore,
            MockHelper.configModelStore()
        )
        val operations = listOf<Operation>(LoginUserOperation(appId, localOneSignalId, null, null))

        /* When */
        val response = loginUserOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.SUCCESS
        coVerify(exactly = 1) {
            mockUserBackendService.createUser(
                appId,
                mapOf(),
                any(),
                any()
            )
        }
    }

    test("login anonymous user fails with retry when network condition exists") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } throws BackendException(408, "TIMEOUT")

        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()

        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor = LoginUserOperationExecutor(mockIdentityOperationExecutor, MockHelper.applicationService(), MockHelper.deviceService(), mockUserBackendService, mockIdentityModelStore, mockPropertiesModelStore, mockSubscriptionsModelStore, MockHelper.configModelStore())
        val operations = listOf<Operation>(LoginUserOperation(appId, localOneSignalId, null, null))

        /* When */
        val response = loginUserOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.FAIL_RETRY
        coVerify(exactly = 1) { mockUserBackendService.createUser(appId, mapOf(), any(), any()) }
    }

    test("login anonymous user fails with no retry when backend error condition exists") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } throws BackendException(404, "NOT FOUND")

        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()

        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor = LoginUserOperationExecutor(mockIdentityOperationExecutor, MockHelper.applicationService(), MockHelper.deviceService(), mockUserBackendService, mockIdentityModelStore, mockPropertiesModelStore, mockSubscriptionsModelStore, MockHelper.configModelStore())
        val operations = listOf<Operation>(LoginUserOperation(appId, localOneSignalId, null, null))

        /* When */
        val response = loginUserOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.FAIL_NORETRY
        coVerify(exactly = 1) { mockUserBackendService.createUser(appId, mapOf(), any(), any()) }
    }

    test("login identified user without association successfully creates user") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } returns
            CreateUserResponse(mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId), PropertiesObject(), listOf())

        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()

        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor = LoginUserOperationExecutor(mockIdentityOperationExecutor, MockHelper.applicationService(), MockHelper.deviceService(), mockUserBackendService, mockIdentityModelStore, mockPropertiesModelStore, mockSubscriptionsModelStore, MockHelper.configModelStore())
        val operations = listOf<Operation>(LoginUserOperation(appId, localOneSignalId, "externalId", null))

        /* When */
        val response = loginUserOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.SUCCESS
        coVerify(exactly = 1) { mockUserBackendService.createUser(appId, mapOf(IdentityConstants.EXTERNAL_ID to "externalId"), any(), any()) }
    }

    test("login identified user with association succeeds when association is successful") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()

        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()
        coEvery { mockIdentityOperationExecutor.execute(any()) } returns ExecutionResponse(ExecutionResult.SUCCESS)

        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor = LoginUserOperationExecutor(mockIdentityOperationExecutor, MockHelper.applicationService(), MockHelper.deviceService(), mockUserBackendService, mockIdentityModelStore, mockPropertiesModelStore, mockSubscriptionsModelStore, MockHelper.configModelStore())
        val operations = listOf<Operation>(LoginUserOperation(appId, localOneSignalId, "externalId", "existingOneSignalId"))

        /* When */
        val response = loginUserOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBeOneOf listOf(ExecutionResult.SUCCESS, ExecutionResult.SUCCESS_STARTING_ONLY)
        coVerify(exactly = 1) {
            mockIdentityOperationExecutor.execute(
                withArg {
                    it.count() shouldBe 1
                    it[0].shouldBeInstanceOf<SetAliasOperation>()
                    (it[0] as SetAliasOperation).appId shouldBe appId
                    (it[0] as SetAliasOperation).onesignalId shouldBe "existingOneSignalId"
                    (it[0] as SetAliasOperation).label shouldBe IdentityConstants.EXTERNAL_ID
                    (it[0] as SetAliasOperation).value shouldBe "externalId"
                }
            )
        }
    }

    test("login identified user with association fails with retry when association fails with retry") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } returns
            CreateUserResponse(mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId), PropertiesObject(), listOf())

        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()
        coEvery { mockIdentityOperationExecutor.execute(any()) } returns ExecutionResponse(ExecutionResult.FAIL_RETRY)

        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor = LoginUserOperationExecutor(mockIdentityOperationExecutor, MockHelper.applicationService(), MockHelper.deviceService(), mockUserBackendService, mockIdentityModelStore, mockPropertiesModelStore, mockSubscriptionsModelStore, MockHelper.configModelStore())
        val operations = listOf<Operation>(LoginUserOperation(appId, localOneSignalId, "externalId", "existingOneSignalId"))

        /* When */
        val response = loginUserOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.FAIL_RETRY
        coVerify(exactly = 1) {
            mockIdentityOperationExecutor.execute(
                withArg {
                    it.count() shouldBe 1
                    it[0].shouldBeInstanceOf<SetAliasOperation>()
                    (it[0] as SetAliasOperation).appId shouldBe appId
                    (it[0] as SetAliasOperation).onesignalId shouldBe "existingOneSignalId"
                    (it[0] as SetAliasOperation).label shouldBe IdentityConstants.EXTERNAL_ID
                    (it[0] as SetAliasOperation).value shouldBe "externalId"
                }
            )
        }
    }

    test("login identified user with association successfully creates user when association fails with no retry") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } returns
            CreateUserResponse(mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId), PropertiesObject(), listOf())

        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()
        coEvery { mockIdentityOperationExecutor.execute(any()) } returns ExecutionResponse(ExecutionResult.FAIL_NORETRY)

        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor = LoginUserOperationExecutor(mockIdentityOperationExecutor, MockHelper.applicationService(), MockHelper.deviceService(), mockUserBackendService, mockIdentityModelStore, mockPropertiesModelStore, mockSubscriptionsModelStore, MockHelper.configModelStore())
        val operations = listOf<Operation>(LoginUserOperation(appId, localOneSignalId, "externalId", "existingOneSignalId"))

        /* When */
        val response = loginUserOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBeOneOf listOf(ExecutionResult.SUCCESS, ExecutionResult.SUCCESS_STARTING_ONLY)
        coVerify(exactly = 1) {
            mockIdentityOperationExecutor.execute(
                withArg {
                    it.count() shouldBe 1
                    it[0].shouldBeInstanceOf<SetAliasOperation>()
                    (it[0] as SetAliasOperation).appId shouldBe appId
                    (it[0] as SetAliasOperation).onesignalId shouldBe "existingOneSignalId"
                    (it[0] as SetAliasOperation).label shouldBe IdentityConstants.EXTERNAL_ID
                    (it[0] as SetAliasOperation).value shouldBe "externalId"
                }
            )
        }
        coVerify(exactly = 1) { mockUserBackendService.createUser(appId, mapOf(IdentityConstants.EXTERNAL_ID to "externalId"), any(), any()) }
    }

    test("login identified user with association fails with retry when association fails with no retry and network condition exists") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } throws BackendException(408, "TIMEOUT")

        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()
        coEvery { mockIdentityOperationExecutor.execute(any()) } returns ExecutionResponse(ExecutionResult.FAIL_NORETRY)

        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor = LoginUserOperationExecutor(mockIdentityOperationExecutor, MockHelper.applicationService(), MockHelper.deviceService(), mockUserBackendService, mockIdentityModelStore, mockPropertiesModelStore, mockSubscriptionsModelStore, MockHelper.configModelStore())
        val operations = listOf<Operation>(LoginUserOperation(appId, localOneSignalId, "externalId", "existingOneSignalId"))

        /* When */
        val response = loginUserOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.FAIL_RETRY
        coVerify(exactly = 1) {
            mockIdentityOperationExecutor.execute(
                withArg {
                    it.count() shouldBe 1
                    it[0].shouldBeInstanceOf<SetAliasOperation>()
                    (it[0] as SetAliasOperation).appId shouldBe appId
                    (it[0] as SetAliasOperation).onesignalId shouldBe "existingOneSignalId"
                    (it[0] as SetAliasOperation).label shouldBe IdentityConstants.EXTERNAL_ID
                    (it[0] as SetAliasOperation).value shouldBe "externalId"
                }
            )
        }
        coVerify(exactly = 1) { mockUserBackendService.createUser(appId, mapOf(IdentityConstants.EXTERNAL_ID to "externalId"), any(), any()) }
    }

    test("creating user will merge operations into one backend call") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } returns
            CreateUserResponse(
                mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId),
                PropertiesObject(),
                listOf()
            )
        /* Given */
        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor = LoginUserOperationExecutor(
            mockIdentityOperationExecutor,
            AndroidMockHelper.applicationService(),
            MockHelper.deviceService(),
            mockUserBackendService,
            mockIdentityModelStore,
            mockPropertiesModelStore,
            mockSubscriptionsModelStore,
            MockHelper.configModelStore()
        )
        val operations = listOf<Operation>(
            LoginUserOperation(appId, localOneSignalId, null, null),
            SetAliasOperation(appId, localOneSignalId, "aliasLabel1", "aliasValue1-1"),
            SetAliasOperation(appId, localOneSignalId, "aliasLabel1", "aliasValue1-2"),
            SetAliasOperation(appId, localOneSignalId, "aliasLabel2", "aliasValue2"),
            DeleteAliasOperation(appId, localOneSignalId, "aliasLabel2"),
            CreateSubscriptionOperation(appId, localOneSignalId, "subscriptionId1", SubscriptionType.PUSH, true, "pushToken1", SubscriptionStatus.SUBSCRIBED),
            UpdateSubscriptionOperation(appId, localOneSignalId, "subscriptionId1", SubscriptionType.PUSH, true, "pushToken2", SubscriptionStatus.SUBSCRIBED),
            CreateSubscriptionOperation(appId, localOneSignalId, "subscriptionId2", SubscriptionType.EMAIL, true, "name@company.com", SubscriptionStatus.SUBSCRIBED),
            DeleteSubscriptionOperation(appId, localOneSignalId, "subscriptionId2"),
            SetTagOperation(appId, localOneSignalId, "tagKey1", "tagValue1-1"),
            SetTagOperation(appId, localOneSignalId, "tagKey1", "tagValue1-2"),
            SetTagOperation(appId, localOneSignalId, "tagKey2", "tagValue2"),
            DeleteTagOperation(appId, localOneSignalId, "tagKey2"),
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
            mockUserBackendService.createUser(
                appId,
                mapOf("aliasLabel1" to "aliasValue1-2"),
                withArg {
                    it.country shouldBe "country"
                    it.language shouldBe "lang2"
                    it.timezoneId shouldBe "timezone"
                    it.latitude shouldBe 123.45
                    it.longitude shouldBe 678.90
                    it.tags shouldBe mapOf("tagKey1" to "tagValue1-2")
                },
                withArg {
                    it.count() shouldBe 1
                    it[0].type shouldBe SubscriptionObjectType.ANDROID_PUSH
                    it[0].enabled shouldBe true
                    it[0].token shouldBe "pushToken2"
                    it[0].notificationTypes shouldBe SubscriptionStatus.SUBSCRIBED
                }
            )
        }
    }

    test("creating user will hydrate when the user hasn't changed") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } returns
            CreateUserResponse(
                mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId),
                PropertiesObject(),
                listOf(SubscriptionObject(remoteSubscriptionId1, SubscriptionObjectType.ANDROID_PUSH), SubscriptionObject(remoteSubscriptionId2, SubscriptionObjectType.EMAIL))
            )
        /* Given */
        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()

        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockIdentityModel = IdentityModel()
        mockIdentityModel.onesignalId = localOneSignalId
        every { mockIdentityModelStore.model } returns mockIdentityModel

        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockPropertiesModel = PropertiesModel()
        mockPropertiesModel.onesignalId = localOneSignalId
        every { mockPropertiesModelStore.model } returns mockPropertiesModel

        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        val subscriptionModel1 = SubscriptionModel()
        subscriptionModel1.id = localSubscriptionId1
        val subscriptionModel2 = SubscriptionModel()
        subscriptionModel2.id = localSubscriptionId2
        every { mockSubscriptionsModelStore.get(localSubscriptionId1) } returns subscriptionModel1
        every { mockSubscriptionsModelStore.get(localSubscriptionId2) } returns subscriptionModel2

        val loginUserOperationExecutor = LoginUserOperationExecutor(
            mockIdentityOperationExecutor,
            AndroidMockHelper.applicationService(),
            MockHelper.deviceService(),
            mockUserBackendService,
            mockIdentityModelStore,
            mockPropertiesModelStore,
            mockSubscriptionsModelStore,
            MockHelper.configModelStore()
        )
        val operations = listOf<Operation>(
            LoginUserOperation(appId, localOneSignalId, null, null),
            CreateSubscriptionOperation(appId, localOneSignalId, localSubscriptionId1, SubscriptionType.PUSH, true, "pushToken1", SubscriptionStatus.SUBSCRIBED),
            CreateSubscriptionOperation(appId, localOneSignalId, localSubscriptionId2, SubscriptionType.EMAIL, true, "name@company.com", SubscriptionStatus.SUBSCRIBED)
        )

        /* When */
        val response = loginUserOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.SUCCESS

        mockIdentityModel.onesignalId shouldBe remoteOneSignalId
        mockPropertiesModel.onesignalId shouldBe remoteOneSignalId
        subscriptionModel1.id shouldBe remoteSubscriptionId1
        subscriptionModel2.id shouldBe remoteSubscriptionId2

        coVerify(exactly = 1) {
            mockUserBackendService.createUser(
                appId,
                mapOf(),
                any(),
                any()
            )
        }
    }

    test("creating user will not hydrate when the user has changed") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } returns
            CreateUserResponse(
                mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId),
                PropertiesObject(),
                listOf(SubscriptionObject(remoteSubscriptionId1, SubscriptionObjectType.ANDROID_PUSH), SubscriptionObject(remoteSubscriptionId2, SubscriptionObjectType.EMAIL))
            )
        /* Given */
        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()

        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockIdentityModel = IdentityModel()
        mockIdentityModel.onesignalId = "new-local-onesignalId"
        every { mockIdentityModelStore.model } returns mockIdentityModel

        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockPropertiesModel = PropertiesModel()
        mockPropertiesModel.onesignalId = "new-local-onesignalId"
        every { mockPropertiesModelStore.model } returns mockPropertiesModel

        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        val subscriptionModel1 = SubscriptionModel()
        subscriptionModel1.id = "new-local-subscriptionId1"
        val subscriptionModel2 = SubscriptionModel()
        subscriptionModel2.id = "new-local-subscriptionId2"
        every { mockSubscriptionsModelStore.get(localSubscriptionId1) } returns null
        every { mockSubscriptionsModelStore.get(localSubscriptionId2) } returns null

        val loginUserOperationExecutor = LoginUserOperationExecutor(
            mockIdentityOperationExecutor,
            AndroidMockHelper.applicationService(),
            MockHelper.deviceService(),
            mockUserBackendService,
            mockIdentityModelStore,
            mockPropertiesModelStore,
            mockSubscriptionsModelStore,
            MockHelper.configModelStore()
        )
        val operations = listOf<Operation>(
            LoginUserOperation(appId, localOneSignalId, null, null),
            CreateSubscriptionOperation(appId, localOneSignalId, localSubscriptionId1, SubscriptionType.PUSH, true, "pushToken1", SubscriptionStatus.SUBSCRIBED),
            CreateSubscriptionOperation(appId, localOneSignalId, localSubscriptionId2, SubscriptionType.EMAIL, true, "name@company.com", SubscriptionStatus.SUBSCRIBED)
        )

        /* When */
        val response = loginUserOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.SUCCESS

        mockIdentityModel.onesignalId shouldBe "new-local-onesignalId"
        mockPropertiesModel.onesignalId shouldBe "new-local-onesignalId"
        subscriptionModel1.id shouldBe "new-local-subscriptionId1"
        subscriptionModel2.id shouldBe "new-local-subscriptionId2"

        coVerify(exactly = 1) {
            mockUserBackendService.createUser(
                appId,
                mapOf(),
                any(),
                any()
            )
        }
    }

    test("creating user will provide local to remote translations") {
        /* Given */
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } returns
            CreateUserResponse(
                mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId),
                PropertiesObject(),
                listOf(SubscriptionObject(remoteSubscriptionId1, SubscriptionObjectType.ANDROID_PUSH), SubscriptionObject(remoteSubscriptionId2, SubscriptionObjectType.EMAIL))
            )
        /* Given */
        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        every { mockSubscriptionsModelStore.get(any()) } returns null

        val loginUserOperationExecutor = LoginUserOperationExecutor(
            mockIdentityOperationExecutor,
            AndroidMockHelper.applicationService(),
            MockHelper.deviceService(),
            mockUserBackendService,
            mockIdentityModelStore,
            mockPropertiesModelStore,
            mockSubscriptionsModelStore,
            MockHelper.configModelStore()
        )
        val operations = listOf<Operation>(
            LoginUserOperation(appId, localOneSignalId, null, null),
            CreateSubscriptionOperation(appId, localOneSignalId, localSubscriptionId1, SubscriptionType.PUSH, true, "pushToken1", SubscriptionStatus.SUBSCRIBED),
            CreateSubscriptionOperation(appId, localOneSignalId, localSubscriptionId2, SubscriptionType.EMAIL, true, "name@company.com", SubscriptionStatus.SUBSCRIBED)
        )

        /* When */
        val response = loginUserOperationExecutor.execute(operations)

        /* Then */
        response.result shouldBe ExecutionResult.SUCCESS
        response.idTranslations shouldBe mapOf(localOneSignalId to remoteOneSignalId, localSubscriptionId1 to remoteSubscriptionId1, localSubscriptionId2 to remoteSubscriptionId2)
        coVerify(exactly = 1) {
            mockUserBackendService.createUser(
                appId,
                mapOf(),
                any(),
                any()
            )
        }
    }
})
