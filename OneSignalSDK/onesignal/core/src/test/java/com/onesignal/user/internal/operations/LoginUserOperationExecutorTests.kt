package com.onesignal.user.internal.operations

import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.Operation
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk

@RobolectricTest
class LoginUserOperationExecutorTests : FunSpec({
    val appId = "appId"
    val localOneSignalId = "local-onesignalId"
    val remoteOneSignalId = "remote-onesignalId"
    val localSubscriptionId1 = "local-subscriptionId1"
    val localSubscriptionId2 = "local-subscriptionId2"
    val remoteSubscriptionId1 = "remote-subscriptionId1"
    val remoteSubscriptionId2 = "remote-subscriptionId2"
    val createSubscriptionOperation =
        CreateSubscriptionOperation(
            appId,
            localOneSignalId,
            "subscriptionId1",
            SubscriptionType.PUSH,
            true,
            "pushToken1",
            SubscriptionStatus.SUBSCRIBED,
        )

    test("login anonymous user successfully creates user") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } returns
            CreateUserResponse(
                mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId),
                PropertiesObject(),
                listOf(),
            )
        // Given
        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor =
            LoginUserOperationExecutor(
                mockIdentityOperationExecutor,
                AndroidMockHelper.applicationService(),
                MockHelper.deviceService(),
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                MockHelper.configModelStore(),
                MockHelper.languageContext(),
            )
        val operations =
            listOf<Operation>(
                LoginUserOperation(appId, localOneSignalId, null, null),
                createSubscriptionOperation,
            )

        // When
        val response = loginUserOperationExecutor.execute(operations)

        // Then
        response.result shouldBe ExecutionResult.SUCCESS
        coVerify(exactly = 1) {
            mockUserBackendService.createUser(
                appId,
                mapOf(),
                any(),
                any(),
            )
        }
    }

    test("login anonymous user fails with retry when network condition exists") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } throws BackendException(408, "TIMEOUT", retryAfterSeconds = 10)

        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()

        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor =
            LoginUserOperationExecutor(
                mockIdentityOperationExecutor,
                AndroidMockHelper.applicationService(),
                MockHelper.deviceService(),
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                MockHelper.configModelStore(),
                MockHelper.languageContext(),
            )
        val operations =
            listOf<Operation>(
                LoginUserOperation(appId, localOneSignalId, null, null),
                createSubscriptionOperation,
            )

        // When
        val response = loginUserOperationExecutor.execute(operations)

        // Then
        response.result shouldBe ExecutionResult.FAIL_RETRY
        response.retryAfterSeconds shouldBe 10
        coVerify(exactly = 1) { mockUserBackendService.createUser(appId, mapOf(), any(), any()) }
    }

    test("login anonymous user fails with no retry when backend error condition exists") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } throws BackendException(404, "NOT FOUND")

        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()

        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor =
            LoginUserOperationExecutor(mockIdentityOperationExecutor, AndroidMockHelper.applicationService(), MockHelper.deviceService(), mockUserBackendService, mockIdentityModelStore, mockPropertiesModelStore, mockSubscriptionsModelStore, MockHelper.configModelStore(), MockHelper.languageContext())
        val operations =
            listOf<Operation>(
                LoginUserOperation(appId, localOneSignalId, null, null),
                createSubscriptionOperation,
            )

        // When
        val response = loginUserOperationExecutor.execute(operations)

        // Then
        response.result shouldBe ExecutionResult.FAIL_NORETRY
        coVerify(exactly = 1) { mockUserBackendService.createUser(appId, mapOf(), any(), any()) }
    }

    test("login identified user without association successfully creates user") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } returns
            CreateUserResponse(mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId), PropertiesObject(), listOf())

        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()

        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor =
            LoginUserOperationExecutor(mockIdentityOperationExecutor, MockHelper.applicationService(), MockHelper.deviceService(), mockUserBackendService, mockIdentityModelStore, mockPropertiesModelStore, mockSubscriptionsModelStore, MockHelper.configModelStore(), MockHelper.languageContext())
        val operations = listOf<Operation>(LoginUserOperation(appId, localOneSignalId, "externalId", null))

        // When
        val response = loginUserOperationExecutor.execute(operations)

        // Then
        response.result shouldBe ExecutionResult.SUCCESS
        coVerify(
            exactly = 1,
        ) { mockUserBackendService.createUser(appId, mapOf(IdentityConstants.EXTERNAL_ID to "externalId"), any(), any()) }
    }

    // If the User is identified then the backend may have found an existing User, if so
    // we need to refresh it so we get all it's full subscription list
    test("login identified user returns result with RefreshUser") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } returns
            CreateUserResponse(mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId), PropertiesObject(), listOf())

        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()

        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor =
            LoginUserOperationExecutor(
                mockIdentityOperationExecutor,
                MockHelper.applicationService(),
                MockHelper.deviceService(),
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                MockHelper.configModelStore(),
                MockHelper.languageContext(),
            )
        val operations = listOf<Operation>(LoginUserOperation(appId, localOneSignalId, "externalId", null))

        // When
        val response = loginUserOperationExecutor.execute(operations)

        // Then
        response.result shouldBe ExecutionResult.SUCCESS
        response.operations!!.size shouldBe 1
        (response.operations!![0] as RefreshUserOperation).let {
            it.shouldBeInstanceOf<RefreshUserOperation>()
            it.appId shouldBe appId
            it.onesignalId shouldBe remoteOneSignalId
        }
    }

    test("login identified user with association succeeds when association is successful") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()

        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()
        coEvery { mockIdentityOperationExecutor.execute(any()) } returns ExecutionResponse(ExecutionResult.SUCCESS)

        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor =
            LoginUserOperationExecutor(mockIdentityOperationExecutor, MockHelper.applicationService(), MockHelper.deviceService(), mockUserBackendService, mockIdentityModelStore, mockPropertiesModelStore, mockSubscriptionsModelStore, MockHelper.configModelStore(), MockHelper.languageContext())
        val operations = listOf<Operation>(LoginUserOperation(appId, localOneSignalId, "externalId", "existingOneSignalId"))

        // When
        val response = loginUserOperationExecutor.execute(operations)

        // Then
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
                },
            )
        }
    }

    test("login identified user with association fails with retry when association fails with retry") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } returns
            CreateUserResponse(mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId), PropertiesObject(), listOf())

        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()
        coEvery { mockIdentityOperationExecutor.execute(any()) } returns ExecutionResponse(ExecutionResult.FAIL_RETRY)

        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor =
            LoginUserOperationExecutor(mockIdentityOperationExecutor, MockHelper.applicationService(), MockHelper.deviceService(), mockUserBackendService, mockIdentityModelStore, mockPropertiesModelStore, mockSubscriptionsModelStore, MockHelper.configModelStore(), MockHelper.languageContext())
        val operations = listOf<Operation>(LoginUserOperation(appId, localOneSignalId, "externalId", "existingOneSignalId"))

        // When
        val response = loginUserOperationExecutor.execute(operations)

        // Then
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
                },
            )
        }
    }

    test("login identified user with association successfully creates user when association fails with no retry") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } returns
            CreateUserResponse(mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId), PropertiesObject(), listOf())

        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()
        coEvery { mockIdentityOperationExecutor.execute(any()) } returns ExecutionResponse(ExecutionResult.FAIL_NORETRY)

        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor =
            LoginUserOperationExecutor(mockIdentityOperationExecutor, MockHelper.applicationService(), MockHelper.deviceService(), mockUserBackendService, mockIdentityModelStore, mockPropertiesModelStore, mockSubscriptionsModelStore, MockHelper.configModelStore(), MockHelper.languageContext())
        val operations = listOf<Operation>(LoginUserOperation(appId, localOneSignalId, "externalId", "existingOneSignalId"))

        // When
        val response = loginUserOperationExecutor.execute(operations)

        // Then
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
                },
            )
        }
        coVerify(
            exactly = 1,
        ) { mockUserBackendService.createUser(appId, mapOf(IdentityConstants.EXTERNAL_ID to "externalId"), any(), any()) }
    }

    test("login identified user with association fails with retry when association fails with no retry and network condition exists") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } throws BackendException(408, "TIMEOUT")

        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()
        coEvery { mockIdentityOperationExecutor.execute(any()) } returns ExecutionResponse(ExecutionResult.FAIL_NORETRY)

        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor =
            LoginUserOperationExecutor(mockIdentityOperationExecutor, MockHelper.applicationService(), MockHelper.deviceService(), mockUserBackendService, mockIdentityModelStore, mockPropertiesModelStore, mockSubscriptionsModelStore, MockHelper.configModelStore(), MockHelper.languageContext())
        val operations = listOf<Operation>(LoginUserOperation(appId, localOneSignalId, "externalId", "existingOneSignalId"))

        // When
        val response = loginUserOperationExecutor.execute(operations)

        // Then
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
                },
            )
        }
        coVerify(
            exactly = 1,
        ) { mockUserBackendService.createUser(appId, mapOf(IdentityConstants.EXTERNAL_ID to "externalId"), any(), any()) }
    }

    test("creating user will merge operations into one backend call") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } returns
            CreateUserResponse(
                mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId),
                PropertiesObject(),
                listOf(),
            )
        // Given
        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor =
            LoginUserOperationExecutor(
                mockIdentityOperationExecutor,
                AndroidMockHelper.applicationService(),
                MockHelper.deviceService(),
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                MockHelper.configModelStore(),
                MockHelper.languageContext(),
            )
        val operations =
            listOf<Operation>(
                LoginUserOperation(appId, localOneSignalId, null, null),
                CreateSubscriptionOperation(
                    appId,
                    localOneSignalId,
                    "subscriptionId1",
                    SubscriptionType.PUSH,
                    true,
                    "pushToken1",
                    SubscriptionStatus.SUBSCRIBED,
                ),
                UpdateSubscriptionOperation(
                    appId,
                    localOneSignalId,
                    "subscriptionId1",
                    SubscriptionType.PUSH,
                    true,
                    "pushToken2",
                    SubscriptionStatus.SUBSCRIBED,
                ),
                CreateSubscriptionOperation(
                    appId,
                    localOneSignalId,
                    "subscriptionId2",
                    SubscriptionType.EMAIL,
                    true,
                    "name@company.com",
                    SubscriptionStatus.SUBSCRIBED,
                ),
                DeleteSubscriptionOperation(appId, localOneSignalId, "subscriptionId2"),
            )

        // When
        val response = loginUserOperationExecutor.execute(operations)

        // Then
        response.result shouldBe ExecutionResult.SUCCESS
        coVerify(exactly = 1) {
            mockUserBackendService.createUser(
                appId,
                // Aliases omitted intentionally in PR #1794, to avoid failed create user calls.
                //  - Ideally we batch as much as possible into the create as most of the time it
                //    should be successful. Then when there are failures omit the "bad data" and try
                //    again, however this is more complex which is why it wasn't done initially.
                mapOf(),
                withArg {
                    it.count() shouldBe 1
                    val subscription = it[0]
                    subscription.type shouldBe SubscriptionObjectType.ANDROID_PUSH
                    subscription.enabled shouldBe true
                    subscription.token shouldBe "pushToken2"
                    SubscriptionStatus.fromInt(subscription.notificationTypes!!) shouldBe SubscriptionStatus.SUBSCRIBED
                },
                any(),
            )
        }
    }

    test("creating user will hydrate when the user hasn't changed") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } returns
            CreateUserResponse(
                mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId),
                PropertiesObject(),
                listOf(SubscriptionObject(remoteSubscriptionId1, SubscriptionObjectType.ANDROID_PUSH), SubscriptionObject(remoteSubscriptionId2, SubscriptionObjectType.EMAIL)),
            )
        // Given
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

        val loginUserOperationExecutor =
            LoginUserOperationExecutor(
                mockIdentityOperationExecutor,
                AndroidMockHelper.applicationService(),
                MockHelper.deviceService(),
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                MockHelper.configModelStore(),
                MockHelper.languageContext(),
            )
        val operations =
            listOf<Operation>(
                LoginUserOperation(appId, localOneSignalId, null, null),
                CreateSubscriptionOperation(
                    appId,
                    localOneSignalId,
                    localSubscriptionId1,
                    SubscriptionType.PUSH,
                    true,
                    "pushToken1",
                    SubscriptionStatus.SUBSCRIBED,
                ),
                CreateSubscriptionOperation(
                    appId,
                    localOneSignalId,
                    localSubscriptionId2,
                    SubscriptionType.EMAIL,
                    true,
                    "name@company.com",
                    SubscriptionStatus.SUBSCRIBED,
                ),
            )

        // When
        val response = loginUserOperationExecutor.execute(operations)

        // Then
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
                any(),
            )
        }
    }

    test("creating user will not hydrate when the user has changed") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } returns
            CreateUserResponse(
                mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId),
                PropertiesObject(),
                listOf(SubscriptionObject(remoteSubscriptionId1, SubscriptionObjectType.ANDROID_PUSH), SubscriptionObject(remoteSubscriptionId2, SubscriptionObjectType.EMAIL)),
            )
        // Given
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

        val loginUserOperationExecutor =
            LoginUserOperationExecutor(
                mockIdentityOperationExecutor,
                AndroidMockHelper.applicationService(),
                MockHelper.deviceService(),
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                MockHelper.configModelStore(),
                MockHelper.languageContext(),
            )
        val operations =
            listOf<Operation>(
                LoginUserOperation(appId, localOneSignalId, null, null),
                CreateSubscriptionOperation(
                    appId,
                    localOneSignalId,
                    localSubscriptionId1,
                    SubscriptionType.PUSH,
                    true,
                    "pushToken1",
                    SubscriptionStatus.SUBSCRIBED,
                ),
                CreateSubscriptionOperation(
                    appId,
                    localOneSignalId,
                    localSubscriptionId2,
                    SubscriptionType.EMAIL,
                    true,
                    "name@company.com",
                    SubscriptionStatus.SUBSCRIBED,
                ),
            )

        // When
        val response = loginUserOperationExecutor.execute(operations)

        // Then
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
                any(),
            )
        }
    }

    test("creating user will provide local to remote translations") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } returns
            CreateUserResponse(
                mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId),
                PropertiesObject(),
                listOf(SubscriptionObject(remoteSubscriptionId1, SubscriptionObjectType.ANDROID_PUSH), SubscriptionObject(remoteSubscriptionId2, SubscriptionObjectType.EMAIL)),
            )
        // Given
        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        every { mockSubscriptionsModelStore.get(any()) } returns null

        val loginUserOperationExecutor =
            LoginUserOperationExecutor(
                mockIdentityOperationExecutor,
                AndroidMockHelper.applicationService(),
                MockHelper.deviceService(),
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                MockHelper.configModelStore(),
                MockHelper.languageContext(),
            )
        val operations =
            listOf<Operation>(
                LoginUserOperation(appId, localOneSignalId, null, null),
                CreateSubscriptionOperation(
                    appId,
                    localOneSignalId,
                    localSubscriptionId1,
                    SubscriptionType.PUSH,
                    true,
                    "pushToken1",
                    SubscriptionStatus.SUBSCRIBED,
                ),
                CreateSubscriptionOperation(
                    appId,
                    localOneSignalId,
                    localSubscriptionId2,
                    SubscriptionType.EMAIL,
                    true,
                    "name@company.com",
                    SubscriptionStatus.SUBSCRIBED,
                ),
            )

        // When
        val response = loginUserOperationExecutor.execute(operations)

        // Then
        response.result shouldBe ExecutionResult.SUCCESS
        response.idTranslations shouldBe mapOf(localOneSignalId to remoteOneSignalId, localSubscriptionId1 to remoteSubscriptionId1, localSubscriptionId2 to remoteSubscriptionId2)
        coVerify(exactly = 1) {
            mockUserBackendService.createUser(
                appId,
                mapOf(),
                any(),
                any(),
            )
        }
    }

    test("ensure anonymous login with no other operations will fail with FAIL_NORETRY") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } returns
            CreateUserResponse(mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId), PropertiesObject(), listOf())

        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()

        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()

        val loginUserOperationExecutor =
            LoginUserOperationExecutor(
                mockIdentityOperationExecutor,
                MockHelper.applicationService(),
                MockHelper.deviceService(),
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                MockHelper.configModelStore(),
                MockHelper.languageContext(),
            )
        // anonymous Login request
        val operations = listOf<Operation>(LoginUserOperation(appId, localOneSignalId, null, null))

        // When
        val response = loginUserOperationExecutor.execute(operations)

        // Then
        response.result shouldBe ExecutionResult.FAIL_NORETRY
        // ensure user is not created by the bad request
        coVerify(
            exactly = 0,
        ) { mockUserBackendService.createUser(appId, any(), any(), any()) }
    }

    test("create user maps subscriptions when backend order is different (match by id/token)") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        // backend returns EMAIL first (with token), then PUSH — out of order
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } returns
            CreateUserResponse(
                mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId),
                PropertiesObject(),
                listOf(
                    SubscriptionObject(id = remoteSubscriptionId2, type = SubscriptionObjectType.EMAIL, token = "name@company.com"),
                    SubscriptionObject(id = remoteSubscriptionId1, type = SubscriptionObjectType.ANDROID_PUSH, token = "pushToken2"),
                ),
            )

        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        every { mockSubscriptionsModelStore.get(any()) } returns null

        val executor =
            LoginUserOperationExecutor(
                mockIdentityOperationExecutor,
                AndroidMockHelper.applicationService(),
                MockHelper.deviceService(),
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                MockHelper.configModelStore(),
                MockHelper.languageContext(),
            )

        // send PUSH then EMAIL (local IDs 1,2) — order differs from backend response
        val ops =
            listOf(
                LoginUserOperation(appId, localOneSignalId, null, null),
                CreateSubscriptionOperation(appId, localOneSignalId, localSubscriptionId1, SubscriptionType.PUSH, true, "pushToken2", SubscriptionStatus.SUBSCRIBED),
                CreateSubscriptionOperation(appId, localOneSignalId, localSubscriptionId2, SubscriptionType.EMAIL, true, "name@company.com", SubscriptionStatus.SUBSCRIBED),
            )

        // When
        val response = executor.execute(ops)

        // Then
        response.result shouldBe ExecutionResult.SUCCESS
        // ensure local to remote mapping is correct despite different order
        response.idTranslations shouldBe
            mapOf(
                localOneSignalId to remoteOneSignalId,
                // push
                localSubscriptionId1 to remoteSubscriptionId1,
                // email
                localSubscriptionId2 to remoteSubscriptionId2,
            )
        coVerify(exactly = 1) { mockUserBackendService.createUser(appId, mapOf(), any(), any()) }
    }

    test("create user maps push subscription by type when id and token don't match (case for deleted push sub)") {
        // Given
        val mockUserBackendService = mockk<IUserBackendService>()
        // simulate server-side push sub recreated with new ID and no token; must match by type
        coEvery { mockUserBackendService.createUser(any(), any(), any(), any()) } returns
            CreateUserResponse(
                mapOf(IdentityConstants.ONESIGNAL_ID to remoteOneSignalId),
                PropertiesObject(),
                listOf(
                    SubscriptionObject(id = remoteSubscriptionId1, type = SubscriptionObjectType.ANDROID_PUSH, token = null),
                ),
            )

        val mockIdentityOperationExecutor = mockk<IdentityOperationExecutor>()
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockPropertiesModelStore = MockHelper.propertiesModelStore()

        val mockSubscriptionsModelStore = mockk<SubscriptionModelStore>()
        // provide a local push model so the executor can hydrate its id
        val localPushModel = SubscriptionModel().apply { id = localSubscriptionId1 }
        every { mockSubscriptionsModelStore.get(localSubscriptionId1) } returns localPushModel

        val configModelStore = MockHelper.configModelStore()
        // assume current push sub is the local one we are creating
        configModelStore.model.pushSubscriptionId = localSubscriptionId1

        val executor =
            LoginUserOperationExecutor(
                mockIdentityOperationExecutor,
                AndroidMockHelper.applicationService(),
                MockHelper.deviceService(),
                mockUserBackendService,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockSubscriptionsModelStore,
                configModelStore,
                MockHelper.languageContext(),
            )

        val ops =
            listOf(
                LoginUserOperation(appId, localOneSignalId, null, null),
                CreateSubscriptionOperation(appId, localOneSignalId, localSubscriptionId1, SubscriptionType.PUSH, true, "pushToken1", SubscriptionStatus.SUBSCRIBED),
            )

        // When
        val response = executor.execute(ops)

        // Then
        response.result shouldBe ExecutionResult.SUCCESS
        // should map by type and update both idTranslations and local model
        response.idTranslations shouldBe
            mapOf(
                localOneSignalId to remoteOneSignalId,
                localSubscriptionId1 to remoteSubscriptionId1,
            )
        localPushModel.id shouldBe remoteSubscriptionId1
        // pushSubscriptionId should be updated from local to remote id
        configModelStore.model.pushSubscriptionId shouldBe remoteSubscriptionId1
        coVerify(exactly = 1) { mockUserBackendService.createUser(appId, mapOf(), any(), any()) }
    }
})
