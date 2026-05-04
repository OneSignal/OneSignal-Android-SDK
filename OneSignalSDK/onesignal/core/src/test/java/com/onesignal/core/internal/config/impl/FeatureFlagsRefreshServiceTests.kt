package com.onesignal.core.internal.config.impl

import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.backend.IFeatureFlagsBackendService
import com.onesignal.core.internal.backend.RemoteFeatureFlagsFetchOutcome
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.mocks.IOMockHelper
import com.onesignal.mocks.IOMockHelper.awaitIO
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot

/**
 * Regression coverage for the duplicate Turbine feature-flags fetch at SDK startup.
 *
 * Two startables fire at init:
 *  1. [FeatureFlagsRefreshService.start] -> [IApplicationService.addApplicationLifecycleHandler]
 *     synchronously delivers `onFocus(firedOnSubscribe = true)` while the app is foregrounded,
 *     which kicks off polling and issues fetch #1.
 *  2. [ConfigModelStoreListener.fetchParams] (Android params) finishes and calls
 *     `_configModelStore.replace(config, ModelChangeTags.HYDRATE)`, which fires `onModelReplaced`
 *     on every subscriber. The replaced model carries the **same** `appId`, but pre-fix the
 *     listener still triggered a second poll, producing fetch #2 a few hundred ms after #1.
 *
 * The fix tracks the appId currently being polled and treats a re-trigger for the same appId as
 * a no-op. Tests below assert both directions: same-appId hydrates are deduped, but a genuine
 * appId change still cancels and re-fetches.
 */
class FeatureFlagsRefreshServiceTests : FunSpec({
    listener(IOMockHelper)

    fun mockBackend(): Pair<IFeatureFlagsBackendService, () -> Int> {
        val backend = mockk<IFeatureFlagsBackendService>()
        var count = 0
        coEvery { backend.fetchRemoteFeatureFlags(any()) } answers {
            count++
            RemoteFeatureFlagsFetchOutcome.Unavailable
        }
        return backend to { count }
    }

    /**
     * Builds an [IApplicationService] mock that mirrors production behavior at init:
     * - `addApplicationLifecycleHandler` synchronously fires `onFocus(true)` (matches
     *   [com.onesignal.core.internal.application.impl.ApplicationService.addApplicationLifecycleHandler]
     *   when `current != null`).
     * - `isInForeground` returns the supplied [foregroundSequence] in order; tests pick exactly
     *   the right pattern of `true`/`false` so that each polling loop runs one fetch then exits
     *   on the next iteration. Coupled with `refreshIntervalMs = 0L` this keeps tests
     *   deterministic without needing a virtual time scheduler.
     *
     * Per-trigger budget for [foregroundSequence]:
     * - Initial `start()` -> `onFocus(true)` -> polling loop: **2** values (`true`, `false`).
     * - `onModelReplaced` / `onModelUpdated` event handler condition: **1** value.
     * - Each polling loop kicked off by an event handler when not deduped: **2** values.
     */
    fun foregroundedAppService(vararg foregroundSequence: Boolean): IApplicationService {
        val app = mockk<IApplicationService>()
        val handlerSlot = slot<IApplicationLifecycleHandler>()
        every { app.addApplicationLifecycleHandler(capture(handlerSlot)) } answers {
            handlerSlot.captured.onFocus(true)
        }
        every { app.removeApplicationLifecycleHandler(any()) } just Runs
        every { app.isInForeground } returnsMany foregroundSequence.toList()
        return app
    }

    fun mockConfigStore(model: ConfigModel): ConfigModelStore {
        val store = mockk<ConfigModelStore>()
        every { store.model } returns model
        every { store.subscribe(any()) } just Runs
        every { store.unsubscribe(any()) } just Runs
        return store
    }

    test("HYDRATE replace with the same appId does not trigger a duplicate fetch") {
        val model = ConfigModel().apply { appId = "appId-1" }
        val store = mockConfigStore(model)
        val (backend, fetchCount) = mockBackend()
        // start: [true, false] (loop iter1=true, iter2=false break) + onModelReplaced check: [true]
        val app = foregroundedAppService(true, false, true)

        val service = FeatureFlagsRefreshService(app, store, backend).apply { refreshIntervalMs = 0L }

        // Mirrors `OneSignalImp.internalInit` -> `startupService.scheduleStart()`: synchronously
        // attaches the lifecycle handler, which fires `onFocus(true)` -> initial fetch.
        service.start()
        awaitIO()
        fetchCount() shouldBe 1

        // Mirrors `ConfigModelStoreListener.fetchParams` -> `replace(config, HYDRATE)` with the
        // same appId already on the live model. Pre-fix this fired a second Turbine GET.
        service.onModelReplaced(model, ModelChangeTags.HYDRATE)
        awaitIO()
        fetchCount() shouldBe 1
    }

    test("NORMAL replace with the same appId does not trigger a duplicate fetch") {
        val model = ConfigModel().apply { appId = "appId-1" }
        val store = mockConfigStore(model)
        val (backend, fetchCount) = mockBackend()
        val app = foregroundedAppService(true, false, true)

        val service = FeatureFlagsRefreshService(app, store, backend).apply { refreshIntervalMs = 0L }
        service.start()
        awaitIO()
        fetchCount() shouldBe 1

        service.onModelReplaced(model, ModelChangeTags.NORMAL)
        awaitIO()
        fetchCount() shouldBe 1
    }

    test("HYDRATE replace with a different appId cancels and re-fetches") {
        val model = ConfigModel().apply { appId = "appId-1" }
        val store = mockConfigStore(model)
        val (backend, fetchCount) = mockBackend()
        // start: [true, false] + onModelReplaced check: [true] + new poll loop: [true, false].
        val app = foregroundedAppService(true, false, true, true, false)

        val service = FeatureFlagsRefreshService(app, store, backend).apply { refreshIntervalMs = 0L }
        service.start()
        awaitIO()
        fetchCount() shouldBe 1

        // Genuine appId change (e.g. re-init with a different App ID).
        model.appId = "appId-2"
        service.onModelReplaced(model, ModelChangeTags.HYDRATE)
        awaitIO()
        fetchCount() shouldBe 2

        coVerify(exactly = 1) { backend.fetchRemoteFeatureFlags("appId-1") }
        coVerify(exactly = 1) { backend.fetchRemoteFeatureFlags("appId-2") }
    }

    test("appId property update via onModelUpdated triggers a single re-fetch") {
        val model = ConfigModel().apply { appId = "appId-1" }
        val store = mockConfigStore(model)
        val (backend, fetchCount) = mockBackend()
        // start: [true, false] + onModelUpdated check: [true] + new poll loop: [true, false].
        val app = foregroundedAppService(true, false, true, true, false)

        val service = FeatureFlagsRefreshService(app, store, backend).apply { refreshIntervalMs = 0L }
        service.start()
        awaitIO()
        fetchCount() shouldBe 1

        model.appId = "appId-2"
        service.onModelUpdated(
            com.onesignal.common.modeling.ModelChangedArgs(
                model = model,
                path = ConfigModel::appId.name,
                property = ConfigModel::appId.name,
                oldValue = "appId-1",
                newValue = "appId-2",
            ),
            ModelChangeTags.NORMAL,
        )
        awaitIO()
        fetchCount() shouldBe 2
    }

    test("unfocus then refocus restarts polling for the same appId") {
        val model = ConfigModel().apply { appId = "appId-1" }
        val store = mockConfigStore(model)
        val (backend, fetchCount) = mockBackend()
        // start: [true, false] + onFocus restart loop: [true, false].
        val app = foregroundedAppService(true, false, true, false)

        val service = FeatureFlagsRefreshService(app, store, backend).apply { refreshIntervalMs = 0L }
        service.start()
        awaitIO()
        fetchCount() shouldBe 1

        // onUnfocused must clear the cached pollingAppId so a subsequent refocus is not deduped.
        service.onUnfocused()
        service.onFocus(firedOnSubscribe = false)
        awaitIO()
        fetchCount() shouldBe 2
    }
})
