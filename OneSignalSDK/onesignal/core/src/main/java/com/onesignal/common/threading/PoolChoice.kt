package com.onesignal.common.threading

import android.content.Context
import com.onesignal.core.internal.features.FeatureManager
import com.onesignal.features.FeatureFlag
import com.onesignal.features.StartupFlagsSource

internal class PoolChoice(
    val pools: DispatcherPools,
    val detail: String,
    val warn: Boolean,
    val failure: Throwable? = null,
) {
    val mode: String get() = if (pools is LazyDispatcherPools) "lazy" else "bootstrap"
    val summary: String get() = "$detail; pools=$mode"
}

// Every launch in the process goes through this, so a failed choice must not throw.
// Lazy pools need no flags, and they are what the flag being off already means.
// A failure after an "on" snapshot leaves FeatureManager reporting the flag on over lazy
// pools. The "choice failed" report is what tells those launches apart in telemetry.
@Suppress("TooGenericExceptionCaught")
internal fun choosePools(
    override: Boolean?,
    context: () -> Context?,
): PoolChoice =
    try {
        DispatcherTestHooks.beforePoolChoice?.invoke()
        if (override != null) PoolChoice(poolsFor(override), "test", warn = false) else chooseFromCache(context)
    } catch (t: Throwable) {
        PoolChoice(LazyDispatcherPools(), "off, choice failed", warn = true, failure = t)
    }

private fun chooseFromCache(context: () -> Context?): PoolChoice {
    val flags = FeatureManager.startupFlags(context)
    val enabled = flags.isEnabled(FeatureFlag.SDK_DISPATCHER_BOOTSTRAP)
    val detail =
        when (flags.source) {
            StartupFlagsSource.STORE -> if (enabled) "on" else "off, flag not in cache"
            StartupFlagsSource.MISSING -> "off, no cache yet"
            StartupFlagsSource.UNREADABLE -> "off, cache unreadable"
            StartupFlagsSource.NO_STORE -> "off, no application"
        }
    val degraded = flags.source == StartupFlagsSource.NO_STORE || flags.source == StartupFlagsSource.UNREADABLE
    return PoolChoice(poolsFor(enabled), detail, warn = degraded)
}

private fun poolsFor(enabled: Boolean): DispatcherPools = if (enabled) BootstrapDispatcherPools() else LazyDispatcherPools()
