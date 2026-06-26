package com.onesignal

import com.onesignal.common.threading.OneSignalDispatchers
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec

/**
 * Kotest project-wide config for the :core unit-test module. Kotest 5.8.0 auto-detects a single
 * [AbstractProjectConfig] on the test classpath, so this runs for every spec without per-spec
 * wiring.
 *
 * Now that the background-threading feature flag is gone, every `suspendify*` / `launchOn*` routes
 * unconditionally through the single process-wide [OneSignalDispatchers]. Its pools are
 * intentionally small and bounded, so background work (or a thread parked in a non-cancellable JVM
 * wait) leaked from one spec could otherwise starve later specs that use the real dispatcher.
 * [OneSignalDispatchers.resetForTest] swaps in a clean pool generation and tears the old one down
 * (cancel scopes + `shutdownNow`) around every spec to keep them isolated.
 *
 * Note: this does NOT reset mockk state — leaked mockk stubs of the threading layer are each fixed
 * at their source spec (see e.g. FeatureFlagsRefreshServiceTests' SDK-4507 test, which restores the
 * threading mocks in a `finally`). Doing a blanket `unmockkAll()` here is unsafe: project `beforeSpec`
 * runs *after* a spec's own `IOMockHelper.beforeSpec`, so it would strip the mocks that spec needs.
 */
class OneSignalTestProjectConfig : AbstractProjectConfig() {
    override fun extensions(): List<Extension> = listOf(OneSignalDispatchersResetListener)
}

private object OneSignalDispatchersResetListener : BeforeSpecListener, AfterSpecListener {
    override suspend fun beforeSpec(spec: Spec) {
        OneSignalDispatchers.resetForTest()
    }

    override suspend fun afterSpec(spec: Spec) {
        OneSignalDispatchers.resetForTest()
    }
}
