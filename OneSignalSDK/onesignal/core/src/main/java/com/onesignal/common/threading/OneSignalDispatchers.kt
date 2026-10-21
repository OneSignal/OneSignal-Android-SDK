package com.onesignal.common.threading

import android.content.Context
import android.util.Log
import com.onesignal.core.internal.features.FeatureManager
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.features.FeatureFlag
import com.onesignal.features.StartupFlagsSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Optimized threading manager for the OneSignal SDK.
 *
 * Performance optimizations:
 * - Lazy initialization to reduce startup overhead
 * - Custom thread pools for both IO and Default operations
 * - Optimized thread pool configuration (smaller pools)
 * - Small bounded queues (10 tasks) to prevent memory bloat
 * - Reduced context switching overhead
 * - Efficient thread management with controlled resource usage
 *
 * Made public to allow mocking in tests via IOMockHelper.
 */
@Suppress("TooManyFunctions", "StringLiteralDuplication")
object OneSignalDispatchers {
    private const val TEST_READY_TIMEOUT_MS = 2_000L
    private const val TEST_READY_POLL_MS = 5L
    private const val RELEASE_LOG_TAG = "OneSignal"

    private val inFailureLog = ThreadLocal<Boolean>()

    @Volatile
    private var poolsRef: DispatcherPools? = null
    private val poolsLock = Any()

    @Volatile
    private var appContext: Context? = null

    /** Test override. Null reads the cached flag, and no cache means off. */
    @Volatile
    internal var bootstrapEnabledForTest: Boolean? = null

    // Pools latch on first use, so context has to be recorded before the first prewarm or launch.
    @Suppress("TooGenericExceptionCaught")
    internal fun noteApplicationContext(context: Context) {
        if (appContext != null) return
        appContext =
            try {
                context.applicationContext
            } catch (t: Throwable) {
                Logging.warn("OneSignalDispatchers: application context unavailable: ${t.message}", t)
                null
            }
    }

    internal fun resetBootstrapInputsForTest() {
        appContext = null
        bootstrapEnabledForTest = null
        FeatureManager.resetStartupFlagsForTest()
    }

    private fun currentPools(): DispatcherPools = poolsRef ?: publishFirstPools()

    // The choice must not launch work: a launch would re-enter here before poolsRef is set.
    // Logging can launch, so a choice failure is logged only after publishing.
    private fun publishFirstPools(): DispatcherPools {
        val choice =
            synchronized(poolsLock) {
                poolsRef?.let { return it }
                choosePoolsOrLazy().also { poolsRef = it.pools }
            }
        choice.failure?.let(::logChoiceFailure)
        if (bootstrapEnabledForTest == null) {
            val mode = if (choice.pools is LazyDispatcherPools) "lazy" else "bootstrap"
            logRelease("${choice.detail}; pools=$mode", warn = choice.warn)
        }
        return choice.pools
    }

    private fun newPools(): DispatcherPools {
        val choice = choosePoolsOrLazy()
        choice.failure?.let(::logChoiceFailure)
        return choice.pools
    }

    // Every launch in the process goes through this, so a failed choice must not throw.
    // Lazy pools need no flags, and they are what the flag being off already means.
    @Suppress("TooGenericExceptionCaught")
    private fun choosePoolsOrLazy(): PoolChoice =
        try {
            choosePools()
        } catch (t: Throwable) {
            PoolChoice(LazyDispatcherPools(), "off, choice failed", warn = true, failure = t)
        }

    private fun logChoiceFailure(error: Throwable) = logFailure("pool choice failed, using lazy pools", error)

    private fun choosePools(): PoolChoice {
        DispatcherTestHooks.beforePoolChoice?.invoke()
        val override = bootstrapEnabledForTest
        return if (override != null) {
            PoolChoice(poolsFor(override), "test", false)
        } else {
            chooseFromCache()
        }
    }

    private fun chooseFromCache(): PoolChoice {
        val flags = FeatureManager.startupFlags { appContext ?: rememberApplicationContext() }
        val enabled = flags.isEnabled(FeatureFlag.SDK_DISPATCHER_BOOTSTRAP)
        val detail =
            when (flags.source) {
                StartupFlagsSource.STORE -> if (enabled) "on" else "off, flag not in cache"
                StartupFlagsSource.MISSING -> "off, no cache yet"
                StartupFlagsSource.UNREADABLE -> "off, cache unreadable"
                StartupFlagsSource.NO_STORE -> "off, no application"
            }
        return PoolChoice(poolsFor(enabled), detail, warn = flags.source == StartupFlagsSource.NO_STORE)
    }

    private fun poolsFor(enabled: Boolean): DispatcherPools = if (enabled) BootstrapDispatcherPools() else LazyDispatcherPools()

    private class PoolChoice(
        val pools: DispatcherPools,
        val detail: String,
        val warn: Boolean,
        val failure: Throwable? = null,
    )

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun rememberApplicationContext(): Context? {
        if (appContext != null) return appContext
        // Receivers call prewarm() with no Context when the startup provider is disabled.
        val resolved =
            try {
                val method = Class.forName("android.app.ActivityThread").getMethod("currentApplication")
                method.invoke(null) as? Context
            } catch (_: Throwable) {
                null
            }
        if (resolved != null) appContext = resolved.applicationContext
        return appContext
    }

    /** Logcat, not [Logging]: the SDK log level is often NONE in release, and Logging can dispatch. */
    @Suppress("SwallowedException")
    internal fun logRelease(
        message: String,
        error: Throwable? = null,
        warn: Boolean = error != null,
    ) {
        val line = "sdk_dispatcher_bootstrap: $message"
        try {
            if (warn) Log.w(RELEASE_LOG_TAG, line, error) else Log.i(RELEASE_LOG_TAG, line)
        } catch (_: RuntimeException) {
            // JVM unit tests throw from android.util.Log. A log must not fail the latch.
        }
    }

    /** Lane and pool failures. Also sent through [Logging] so they reach remote telemetry. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    internal fun logFailure(
        message: String,
        error: Throwable,
    ) {
        // Log listeners and visual logging can launch onto the failing lane, which fails and logs again.
        if (inFailureLog.get() == true) return logRelease(message, error)
        inFailureLog.set(true)
        try {
            if (LogLevel.WARN > Logging.logLevel) logRelease(message, error)
            Logging.warn("OneSignalDispatchers: $message", error)
        } catch (_: RuntimeException) {
            logRelease(message, error)
        } finally {
            inFailureLog.remove()
        }
    }

    // Dispatchers and scopes delegate to the current generation so [resetForTest] can swap them.
    val IO: CoroutineDispatcher get() = currentPools().io.dispatcher

    val Default: CoroutineDispatcher get() = currentPools().defaultLane.dispatcher

    val SerialIO: CoroutineDispatcher get() = currentPools().serialIO.dispatcher

    fun launchOnIO(block: suspend () -> Unit): Job {
        return currentPools().ioScope.launch { block() }
    }

    fun launchOnDefault(block: suspend () -> Unit): Job {
        return currentPools().defaultScope.launch { block() }
    }

    /** Launches [block] on the single-thread serial IO dispatcher (FIFO across all callers). */
    fun launchOnSerialIO(block: suspend () -> Unit): Job {
        return currentPools().serialIOScope.launch { block() }
    }

    /** Launches short durable-ingress work on a lane isolated from general SDK I/O. */
    fun launchOnIngress(block: suspend () -> Unit): Job {
        return currentPools().ingressScope.launch { block() }
    }

    @Volatile
    private var prewarmStarted = false
    private val prewarmLock = Any()

    /**
     * Warms every lane off the caller when it can.
     * Bootstrap queues construction. Lazy construction runs on the caller, or blocks behind prewarm.
     */
    @Suppress("TooGenericExceptionCaught")
    fun prewarm() {
        if (prewarmStarted) return
        synchronized(prewarmLock) {
            if (prewarmStarted) return
            prewarmStarted = true
        }
        try {
            currentPools().prewarm()
        } catch (t: Throwable) {
            synchronized(prewarmLock) { prewarmStarted = false }
            logFailure("prewarm failed", t)
        }
    }

    /**
     * Test-only hook to reset [prewarmStarted] so different specs can exercise the
     * "first call wins" branch independently. Not part of any public contract.
     */
    internal fun resetPrewarmForTest() {
        synchronized(prewarmLock) {
            prewarmStarted = false
        }
    }

    /**
     * Test-only hook to clear all in-flight and queued work between specs.
     *
     * Every `suspendify*` / `launchOn*` now routes unconditionally through this single
     * process-wide object (bounded pools: IO core=2/max=3, Default core=2/max=3, queue cap 200).
     * In the full unit-test suite, background work launched by one spec can outlive it and
     * accumulate, saturating the pools and `LinkedBlockingQueue`. Worse, a spec can leave a worker
     * thread parked in a non-cancellable JVM wait (e.g. a `CountDownLatch.await()` that never gets
     * released because the test asserted/failed first); a plain coroutine cancellation cannot free
     * such a thread, so it permanently starves the small pool. Later specs that use the real pool
     * (e.g. HttpClientTests' `launchOnIO {…}.join()`, OperationRepo, LoggerIdResolver) then see
     * `launchOnIO` rejected/cancelled and observe null results.
     *
     * This atomically swaps in a fresh [Pools] generation and tears the old one down —
     * cancelling its scopes and calling `shutdownNow()` to interrupt parked workers. Safe to call
     * even when this object is `mockkObject`-mocked: the affected helpers stub only
     * `prewarm`/`launchOn*`/`suspendify*`, so this real method still runs. Not part of any public
     * contract.
     */
    @Suppress("TooGenericExceptionCaught")
    internal fun resetForTest() {
        FeatureManager.resetStartupFlagsForTest()
        val created = newPools()
        val old: DispatcherPools?
        synchronized(poolsLock) {
            old = poolsRef
            poolsRef = created
        }
        try {
            old?.shutdown()
        } catch (e: Exception) {
            Logging.error("OneSignalDispatchers.resetForTest failed: ${e.message}", e)
        }
        resetPrewarmForTest()
        DispatcherTestHooks.reset()
    }

    @Suppress("ComplexMethod")
    internal fun getPerformanceMetrics(): String {
        val current = currentPools()
        val io = current.io.executor()
        val default = current.defaultLane.executor()
        val serial = current.serialIO.executor()
        val ingress = current.ingress.executor()
        return """
            OneSignalDispatchers Performance Metrics:
            - IO Pool: ${io?.let { "${it.activeCount}/${it.corePoolSize}" } ?: "n/a"} active/core threads
            - IO Queue: ${io?.queue?.size ?: "n/a"} pending tasks
            - Default Pool: ${default?.let { "${it.activeCount}/${it.corePoolSize}" } ?: "n/a"} active/core threads
            - Default Queue: ${default?.queue?.size ?: "n/a"} pending tasks
            - SerialIO Queue: ${serial?.queue?.size ?: "n/a"} pending tasks
            - Ingress Queue: ${ingress?.queue?.size ?: "n/a"} pending tasks
            - Total completed tasks: ${listOfNotNull(io, default, serial, ingress).sumOf { it.completedTaskCount }}
            - Memory usage: ~${listOfNotNull(io, default, serial, ingress).sumOf { it.activeCount } * 1024}KB (thread stacks, ~1MB each)
        """.trimIndent()
    }

    internal fun getStatus(): String {
        val current = currentPools()
        return """
            OneSignalDispatchers Status:
            - IO Executor: ${current.io.status()}
            - Default Executor: ${current.defaultLane.status()}
            - SerialIO Executor: ${current.serialIO.status()}
            - Ingress Executor: ${current.ingress.status()}
            - IO Scope: ${scopeStatus("IOScope") { current.ioScope.isActive }}
            - Default Scope: ${scopeStatus("DefaultScope") { current.defaultScope.isActive }}
            - SerialIO Scope: ${scopeStatus("SerialIOScope") { current.serialIOScope.isActive }}
            - Ingress Scope: ${scopeStatus("IngressScope") { current.ingressScope.isActive }}
        """.trimIndent()
    }

    internal fun awaitReadyForTest(timeoutMs: Long = TEST_READY_TIMEOUT_MS): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val current = currentPools()
            if (listOf(current.io, current.defaultLane, current.serialIO, current.ingress).all { it.status() == "Active" }) {
                return true
            }
            Thread.sleep(TEST_READY_POLL_MS)
        }
        return false
    }

    // internal so tests can exercise the failure branch (when `isShutdown()` itself throws,
    // which happens when the lazy initializer threw and re-throws on every access).
    internal fun executorStatus(
        name: String,
        isShutdown: () -> Boolean,
    ): String =
        try {
            if (isShutdown()) "Shutdown" else "Active"
        } catch (e: Exception) {
            "$name $NOT_INITIALIZED ${e.message ?: UNKNOWN_ERROR}"
        }

    internal fun scopeStatus(
        name: String,
        isActive: () -> Boolean,
    ): String =
        try {
            if (isActive()) "Active" else "Cancelled"
        } catch (e: Exception) {
            "$name $NOT_INITIALIZED ${e.message ?: UNKNOWN_ERROR}"
        }

    private const val NOT_INITIALIZED = "Not initialized"
    private const val UNKNOWN_ERROR = "Unknown error"
}
