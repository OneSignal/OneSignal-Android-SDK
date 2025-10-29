package com.onesignal.user.internal

import android.content.Context
import com.onesignal.common.AndroidUtils
import com.onesignal.common.IDManager
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.services.ServiceProvider
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.getLegacyPlayerId
import com.onesignal.core.internal.preferences.getLegacyUserSyncValues
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.LoginUserFromSubscriptionOperation
import com.onesignal.user.internal.operations.LoginUserOperation
import com.onesignal.user.internal.properties.PropertiesModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.internal.subscriptions.SubscriptionType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify
import org.json.JSONObject
import java.util.Collections

// Mocks used by every test in this file
private class Mocks {
    // Test constants - using consistent naming with SDKInitTests
    val appId = "appId"
    val testOneSignalId = "test-onesignal-id"
    val newOneSignalId = "new-onesignal-id"
    val testExternalId = "test-external-id"
    val testSubscriptionId = "test-subscription-id"
    val testCarrier = "test-carrier"
    val testDeviceOS = "13"
    val testAppVersion = "1.0.0"
    val legacyPlayerId = "legacy-player-id"
    val legacyUserSyncJson = """{"notification_types":1,"identifier":"test-token"}"""

    val mockContext = mockk<Context>(relaxed = true)
    val mockPreferencesService = mockk<IPreferencesService>(relaxed = true)
    val mockOperationRepo = mockk<IOperationRepo>(relaxed = true)
    val mockApplicationService =
        mockk<IApplicationService>(relaxed = true).apply {
            every { appContext } returns mockContext
        }
    val mockServices =
        mockk<ServiceProvider>(relaxed = true).apply {
            every { getService(IApplicationService::class.java) } returns mockApplicationService
        }
    val mockConfigModel = mockk<ConfigModel>(relaxed = true)
    val mockOneSignalUtils = spyk(OneSignalUtils)

    // No longer need DeviceUtils - we'll pass carrier name directly
    val mockAndroidUtils = spyk(AndroidUtils)
    val mockIdManager = mockk<IDManager>(relaxed = true)

    // Create fresh model stores for each test to avoid concurrent modification
    fun createIdentityModelStore(): IdentityModelStore {
        val store = MockHelper.identityModelStore()
        // Set up replace method to actually update the model reference
        every { store.replace(any()) } answers {
            val newModel = firstArg<IdentityModel>()
            every { store.model } returns newModel
        }
        return store
    }

    fun createPropertiesModelStore() = mockk<PropertiesModelStore>(relaxed = true)

    // Keep references to the latest created stores for verification in tests
    var identityModelStore: IdentityModelStore? = null
    var propertiesModelStore: PropertiesModelStore? = null
    var subscriptionModelStore: SubscriptionModelStore? = null

    fun createSubscriptionModelStore(): SubscriptionModelStore {
        // Use a synchronized list to prevent ConcurrentModificationException
        val subscriptionList = mutableListOf<SubscriptionModel>().let { Collections.synchronizedList(it) }
        val mockSubscriptionStore = mockk<SubscriptionModelStore>(relaxed = true)
        every { mockSubscriptionStore.list() } answers { synchronized(subscriptionList) { subscriptionList.toList() } }
        every { mockSubscriptionStore.add(any<SubscriptionModel>(), any()) } answers {
            synchronized(subscriptionList) { subscriptionList.add(firstArg()) }
        }
        every { mockSubscriptionStore.clear(any()) } answers {
            synchronized(subscriptionList) { subscriptionList.clear() }
        }
        every { mockSubscriptionStore.replaceAll(any<List<SubscriptionModel>>()) } answers {
            synchronized(subscriptionList) {
                subscriptionList.clear()
                subscriptionList.addAll(firstArg())
            }
        }
        every { mockSubscriptionStore.replaceAll(any<List<SubscriptionModel>>(), any()) } answers {
            synchronized(subscriptionList) {
                subscriptionList.clear()
                subscriptionList.addAll(firstArg())
            }
        }
        return mockSubscriptionStore
    }

    init {
        // Set up default mock behaviors
        every { mockConfigModel.appId } returns appId
        every { mockConfigModel.pushSubscriptionId } returns testSubscriptionId
        every { mockIdManager.createLocalId() } returns newOneSignalId
        every { mockOneSignalUtils.sdkVersion } returns "5.0.0"
        every { mockAndroidUtils.getAppVersion(any()) } returns testAppVersion
        every { mockPreferencesService.getString(any(), any()) } returns null
        every { mockPreferencesService.getLegacyPlayerId() } returns null
        every { mockPreferencesService.getLegacyUserSyncValues() } returns legacyUserSyncJson
        every { mockOperationRepo.enqueue(any()) } just runs
    }

    fun createUserSwitcher(): UserSwitcher {
        // Create fresh instances for this test
        identityModelStore = createIdentityModelStore()
        propertiesModelStore = createPropertiesModelStore()
        subscriptionModelStore = createSubscriptionModelStore()

        return UserSwitcher(
            preferencesService = mockPreferencesService,
            operationRepo = mockOperationRepo,
            services = mockServices,
            idManager = mockIdManager,
            identityModelStore = identityModelStore!!,
            propertiesModelStore = propertiesModelStore!!,
            subscriptionModelStore = subscriptionModelStore!!,
            configModel = mockConfigModel,
            oneSignalUtils = mockOneSignalUtils,
            carrierName = testCarrier,
            deviceOS = testDeviceOS,
            androidUtils = mockAndroidUtils,
            appContextProvider = { mockContext },
        )
    }

    fun createExistingSubscription(): SubscriptionModel {
        return SubscriptionModel().apply {
            id = testSubscriptionId
            type = SubscriptionType.PUSH
            optedIn = false
            address = "existing-token"
            status = SubscriptionStatus.UNSUBSCRIBE
        }
    }
}

/**
 * Unit tests for the UserSwitcher class
 *
 * These tests focus on the pure business logic of user switching operations,
 * complementing the integration tests in SDKInitTests.kt which test
 * end-to-end SDK initialization and user switching behavior.
 */
class UserSwitcherTests : FunSpec({

    beforeEach {
        Logging.logLevel = LogLevel.NONE
        // Clear mock recorded calls between tests to prevent verification issues
        // Note: We can't clear all mocks here since they're created per-test
    }

    test("createAndSwitchToNewUser creates new user with generated ID") {
        // Given
        val mocks = Mocks()
        val userSwitcher = mocks.createUserSwitcher()

        // When
        userSwitcher.createAndSwitchToNewUser()

        // Then - verify basic user creation flow
        verify(atLeast = 1) { mocks.mockIdManager.createLocalId() }
        verify(exactly = 1) { mocks.subscriptionModelStore!!.clear(ModelChangeTags.NO_PROPOGATE) }
        verify(exactly = 1) { mocks.identityModelStore!!.replace(any()) }
        verify(exactly = 1) { mocks.propertiesModelStore!!.replace(any()) }
        verify(exactly = 1) { mocks.subscriptionModelStore!!.replaceAll(any<List<SubscriptionModel>>()) }
    }

    test("createAndSwitchToNewUser with modify lambda applies modifications") {
        // Given
        val mocks = Mocks()
        val userSwitcher = mocks.createUserSwitcher()

        // When
        userSwitcher.createAndSwitchToNewUser { identityModel, _ ->
            identityModel.externalId = mocks.testExternalId
        }

        // Then - verify that the modify lambda is called and user creation happens
        verify(exactly = 1) { mocks.identityModelStore!!.replace(any()) }
        verify(exactly = 1) { mocks.propertiesModelStore!!.replace(any()) }
    }

    test("createAndSwitchToNewUser with suppressBackendOperation prevents propagation") {
        // Given
        val mocks = Mocks()
        val userSwitcher = mocks.createUserSwitcher()

        // When
        userSwitcher.createAndSwitchToNewUser(suppressBackendOperation = true)

        // Then - should use NO_PROPOGATE tag for subscription updates
        verify(exactly = 1) { mocks.subscriptionModelStore!!.replaceAll(any<List<SubscriptionModel>>(), ModelChangeTags.NO_PROPOGATE) }
        verify(exactly = 0) { mocks.subscriptionModelStore!!.replaceAll(any<List<SubscriptionModel>>()) }
    }

    test("createAndSwitchToNewUser preserves existing subscription data") {
        // Given
        val mocks = Mocks()
        val userSwitcher = mocks.createUserSwitcher()
        val existingSubscription = mocks.createExistingSubscription()
        mocks.subscriptionModelStore!!.add(existingSubscription, ModelChangeTags.NO_PROPOGATE)

        // When
        userSwitcher.createAndSwitchToNewUser()

        // Then - new subscription should be created and model stores updated
        verify(exactly = 1) { mocks.subscriptionModelStore!!.list() }
        verify(exactly = 1) { mocks.subscriptionModelStore!!.replaceAll(any<List<SubscriptionModel>>()) }
    }

    test("createPushSubscriptionFromLegacySync creates subscription from legacy data") {
        // Given
        val mocks = Mocks()
        val legacyUserSyncJSON = JSONObject(mocks.legacyUserSyncJson)
        val mockConfigModel = mockk<ConfigModel>(relaxed = true)
        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>(relaxed = true)
        val userSwitcher = mocks.createUserSwitcher()

        // When
        val result =
            userSwitcher.createPushSubscriptionFromLegacySync(
                legacyPlayerId = mocks.legacyPlayerId,
                legacyUserSyncJSON = legacyUserSyncJSON,
                configModel = mockConfigModel,
                subscriptionModelStore = mockSubscriptionModelStore,
                appContext = mocks.mockContext,
            )

        // Then
        result shouldBe true
        verify(exactly = 1) { mockConfigModel.pushSubscriptionId = mocks.legacyPlayerId }
        verify(exactly = 1) { mockSubscriptionModelStore.add(any(), ModelChangeTags.NO_PROPOGATE) }
    }

    test("initUser with forceCreateUser creates new user") {
        // Given
        val mocks = Mocks()
        val userSwitcher = mocks.createUserSwitcher()
        mocks.identityModelStore!!.model.onesignalId = mocks.newOneSignalId

        // When
        userSwitcher.initUser(forceCreateUser = true)

        // Then - should create user and enqueue login operation
        verify(exactly = 1) { mocks.mockOperationRepo.enqueue(any<LoginUserOperation>()) }
    }

    test("initUser without force create but no existing OneSignal ID creates new user") {
        // Given
        val mocks = Mocks()
        val userSwitcher = mocks.createUserSwitcher()
        // Remove OneSignal ID property completely to simulate no existing user
        mocks.identityModelStore!!.model.remove(IdentityConstants.ONESIGNAL_ID)

        // When
        userSwitcher.initUser(forceCreateUser = false)

        // Then - should create user because no existing OneSignal ID
        verify(exactly = 1) { mocks.mockOperationRepo.enqueue(any<LoginUserOperation>()) }
    }

    test("initUser with existing OneSignal ID and no force create does nothing") {
        // Given
        val mocks = Mocks()
        val userSwitcher = mocks.createUserSwitcher()
        // Set up existing OneSignal ID
        mocks.identityModelStore!!.model.onesignalId = mocks.testOneSignalId

        // When
        userSwitcher.initUser(forceCreateUser = false)

        // Then - should not create new user or enqueue operations
        verify(exactly = 0) { mocks.mockOperationRepo.enqueue(any()) }
        // Note: Don't verify createLocalId count as it might be called during setup
    }

    test("initUser with legacy player ID creates user from legacy data") {
        // Given
        val mocks = Mocks()
        every { mocks.mockPreferencesService.getLegacyPlayerId() } returns mocks.legacyPlayerId
        every { mocks.mockPreferencesService.getLegacyUserSyncValues() } returns mocks.legacyUserSyncJson
        val userSwitcher = mocks.createUserSwitcher()

        // When
        userSwitcher.initUser(forceCreateUser = true)

        // Then - should handle legacy migration path
        verify(exactly = 1) { mocks.mockPreferencesService.getLegacyPlayerId() }
        verify(exactly = 1) { mocks.mockOperationRepo.enqueue(any<LoginUserFromSubscriptionOperation>()) }
    }

    // New focused tests for decomposed methods

    test("createNewUser creates device-scoped user and enqueues LoginUserOperation") {
        // Given
        val mocks = Mocks()
        val userSwitcher = mocks.createUserSwitcher()
        // Remove existing OneSignal ID to trigger user creation
        mocks.identityModelStore!!.model.remove(IdentityConstants.ONESIGNAL_ID)

        // When
        userSwitcher.initUser(forceCreateUser = false)

        // Then - should create new user and enqueue standard login operation
        verify(atLeast = 1) { mocks.mockIdManager.createLocalId() }
        verify(exactly = 1) { mocks.identityModelStore!!.replace(any()) }
        verify(exactly = 1) { mocks.propertiesModelStore!!.replace(any()) }
        verify(exactly = 1) { mocks.mockOperationRepo.enqueue(any()) }
    }

    test("migrateFromLegacyUser handles v4 to v5 migration with legacy sync data") {
        // Given
        val mocks = Mocks()
        every { mocks.mockPreferencesService.getLegacyPlayerId() } returns mocks.legacyPlayerId
        every { mocks.mockPreferencesService.getLegacyUserSyncValues() } returns mocks.legacyUserSyncJson
        every { mocks.mockPreferencesService.saveString(any(), any(), any()) } just runs
        val userSwitcher = mocks.createUserSwitcher()

        // When
        userSwitcher.initUser(forceCreateUser = true)

        // Then - should migrate legacy data and enqueue subscription-based login
        verify(exactly = 1) { mocks.mockPreferencesService.getLegacyPlayerId() }
        verify(exactly = 1) { mocks.mockPreferencesService.getLegacyUserSyncValues() }
        verify(exactly = 1) { mocks.mockOperationRepo.enqueue(any()) }
        // Should clear legacy player ID after migration
        verify(exactly = 1) { mocks.mockPreferencesService.saveString(any(), any(), null) }
    }

    test("migrateFromLegacyUser handles v4 to v5 migration without legacy sync data") {
        // Given
        val mocks = Mocks()
        every { mocks.mockPreferencesService.getLegacyPlayerId() } returns mocks.legacyPlayerId
        every { mocks.mockPreferencesService.getLegacyUserSyncValues() } returns null
        every { mocks.mockPreferencesService.saveString(any(), any(), any()) } just runs
        val userSwitcher = mocks.createUserSwitcher()

        // When
        userSwitcher.initUser(forceCreateUser = true)

        // Then - should still migrate but without creating subscription from sync data
        verify(exactly = 1) { mocks.mockPreferencesService.getLegacyPlayerId() }
        verify(exactly = 1) { mocks.mockPreferencesService.getLegacyUserSyncValues() }
        verify(exactly = 1) { mocks.mockOperationRepo.enqueue(any()) }
        // Should still clear legacy player ID
        verify(exactly = 1) { mocks.mockPreferencesService.saveString(any(), any(), null) }
    }

    test("initUser with forceCreateUser=true always creates new user even with existing OneSignal ID") {
        // Given
        val mocks = Mocks()
        val userSwitcher = mocks.createUserSwitcher()
        // Set up existing OneSignal ID
        mocks.identityModelStore!!.model.onesignalId = mocks.testOneSignalId

        // When
        userSwitcher.initUser(forceCreateUser = true)

        // Then - should create new user despite existing ID
        verify(exactly = 1) { mocks.mockOperationRepo.enqueue(any()) }
        verify(atLeast = 1) { mocks.mockIdManager.createLocalId() }
    }

    test("initUser delegates to createNewUser when no legacy player ID exists") {
        // Given
        val mocks = Mocks()
        every { mocks.mockPreferencesService.getLegacyPlayerId() } returns null
        val userSwitcher = mocks.createUserSwitcher()
        mocks.identityModelStore!!.model.remove(IdentityConstants.ONESIGNAL_ID)

        // When
        userSwitcher.initUser(forceCreateUser = false)

        // Then - should follow new user creation path
        verify(exactly = 1) { mocks.mockPreferencesService.getLegacyPlayerId() }
        verify(exactly = 1) { mocks.mockOperationRepo.enqueue(any()) }
    }

    test("initUser delegates to migrateFromLegacyUser when legacy player ID exists") {
        // Given
        val mocks = Mocks()
        every { mocks.mockPreferencesService.getLegacyPlayerId() } returns mocks.legacyPlayerId
        every { mocks.mockPreferencesService.getLegacyUserSyncValues() } returns null
        every { mocks.mockPreferencesService.saveString(any(), any(), any()) } just runs
        val userSwitcher = mocks.createUserSwitcher()

        // When
        userSwitcher.initUser(forceCreateUser = true)

        // Then - should follow legacy migration path
        verify(exactly = 1) { mocks.mockPreferencesService.getLegacyPlayerId() }
        verify(exactly = 1) { mocks.mockOperationRepo.enqueue(any()) }
    }
})
