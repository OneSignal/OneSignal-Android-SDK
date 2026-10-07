package com.onesignal.core.internal.config.impl

import com.onesignal.core.internal.backend.IParamsBackendService
import com.onesignal.core.internal.backend.ParamsObject
import com.onesignal.core.internal.backend.RemoteLoggingParamsObject
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.debug.LogLevel
import com.onesignal.mocks.IOMockHelper
import com.onesignal.mocks.IOMockHelper.awaitIO
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.user.internal.subscriptions.ISubscriptionManager
import com.onesignal.user.internal.subscriptions.SubscriptionList
import com.onesignal.user.subscriptions.IPushSubscription
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

/**
 * Regression coverage for the snapshot/replace race in [ConfigModelStoreListener.fetchParams]:
 * concurrent in-place writes to sdkRemoteFeatureFlags{,Metadata} from
 * [FeatureFlagsRefreshService] must survive replace().
 */
class ConfigModelStoreListenerTests : FunSpec({
    listener(IOMockHelper)

    suspend fun runFetchWith(
        store: ConfigModelStore,
        remoteLoggingParams: RemoteLoggingParamsObject,
    ) {
        val params = mockk<ParamsObject>(relaxed = true)
        every { params.remoteLoggingParams } returns remoteLoggingParams

        val paramsBackend = mockk<IParamsBackendService>()
        coEvery { paramsBackend.fetchParams(any(), any()) } returns params

        val pushSub = mockk<IPushSubscription>(relaxed = true)
        val subscriptionManager = mockk<ISubscriptionManager>()
        every { subscriptionManager.subscriptions } returns SubscriptionList(emptyList(), pushSub)

        ConfigModelStoreListener(store, paramsBackend, subscriptionManager).start()
        awaitIO()
    }

    // Every other field here is "absent means unchanged". Remote logging is not: the backend
    // withdraws it by dropping log_level, so a level kept from the session before would outlive
    // the revocation in the cache the next cold start reads.

    test("a fetch that omits the log level clears the cached level rather than keeping it") {
        val store = ConfigModelStore(MockPreferencesService())
        store.model.appId = "test-app-id"
        store.model.remoteLoggingParams.logLevel = LogLevel.ERROR
        store.model.remoteLoggingParams.isEnabled = true

        runFetchWith(store, RemoteLoggingParamsObject(logLevel = null))

        store.model.remoteLoggingParams.logLevel shouldBe null
        store.model.remoteLoggingParams.isEnabled shouldBe false
    }

    test("a fetch that sends a log level applies it") {
        val store = ConfigModelStore(MockPreferencesService())
        store.model.appId = "test-app-id"

        runFetchWith(store, RemoteLoggingParamsObject(logLevel = LogLevel.WARN))

        store.model.remoteLoggingParams.logLevel shouldBe LogLevel.WARN
        store.model.remoteLoggingParams.isEnabled shouldBe true
    }

    // The cold-start resolver reads a cached NONE as off, so hydration has to cache it as off too.
    test("a fetch that sends NONE caches remote logging as disabled") {
        val store = ConfigModelStore(MockPreferencesService())
        store.model.appId = "test-app-id"
        store.model.remoteLoggingParams.logLevel = LogLevel.ERROR
        store.model.remoteLoggingParams.isEnabled = true

        runFetchWith(store, RemoteLoggingParamsObject(logLevel = LogLevel.NONE))

        store.model.remoteLoggingParams.logLevel shouldBe LogLevel.NONE
        store.model.remoteLoggingParams.isEnabled shouldBe false
    }

    test("fetchParams does not clobber sdkRemoteFeatureFlags written between snapshot and replace") {
        // Real store so replace() goes through Model.initializeFromModel (data.clear()+putAll()).
        val store = ConfigModelStore(MockPreferencesService())
        store.model.appId = "test-app-id"
        store.model.sdkRemoteFeatureFlags = emptyList()
        store.model.sdkRemoteFeatureFlagMetadata = null

        // Race trigger: locationShared is read between snapshot and replace, so its getter
        // side-effect lands inside the window. Returning null short-circuits the let { }
        // body; only the side-effect matters.
        val params = mockk<ParamsObject>(relaxed = true)
        every { params.locationShared } answers {
            store.model.sdkRemoteFeatureFlags = listOf("sdk_background_threading")
            store.model.sdkRemoteFeatureFlagMetadata = """{"sdk_background_threading":{"x":1}}"""
            null
        }

        val paramsBackend = mockk<IParamsBackendService>()
        coEvery { paramsBackend.fetchParams(any(), any()) } returns params

        val pushSub = mockk<IPushSubscription>(relaxed = true)
        val subscriptionManager = mockk<ISubscriptionManager>()
        every { subscriptionManager.subscriptions } returns SubscriptionList(emptyList(), pushSub)

        val listener = ConfigModelStoreListener(store, paramsBackend, subscriptionManager)
        listener.start()
        awaitIO()

        // Without the fix, the stale snapshot wins and these are [] / null.
        store.model.sdkRemoteFeatureFlags shouldBe listOf("sdk_background_threading")
        store.model.sdkRemoteFeatureFlagMetadata shouldBe """{"sdk_background_threading":{"x":1}}"""
    }

    test("fetchParams preserves sdkRemoteFeatureFlags already on the live model when no in-flight write occurs") {
        // Smoke: no race -> existing values round-trip through snapshot + replace.
        val store = ConfigModelStore(MockPreferencesService())
        store.model.appId = "test-app-id"
        store.model.sdkRemoteFeatureFlags = listOf("sdk_background_threading")
        store.model.sdkRemoteFeatureFlagMetadata = """{"sdk_background_threading":{"x":1}}"""

        val params = mockk<ParamsObject>(relaxed = true)
        val paramsBackend = mockk<IParamsBackendService>()
        coEvery { paramsBackend.fetchParams(any(), any()) } returns params

        val pushSub = mockk<IPushSubscription>(relaxed = true)
        val subscriptionManager = mockk<ISubscriptionManager>()
        every { subscriptionManager.subscriptions } returns SubscriptionList(emptyList(), pushSub)

        val listener = ConfigModelStoreListener(store, paramsBackend, subscriptionManager)
        listener.start()
        awaitIO()

        store.model.sdkRemoteFeatureFlags shouldBe listOf("sdk_background_threading")
        store.model.sdkRemoteFeatureFlagMetadata shouldBe """{"sdk_background_threading":{"x":1}}"""
    }
})
