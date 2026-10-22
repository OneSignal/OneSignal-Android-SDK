package com.onesignal.common.threading

import android.content.Context
import android.util.Log
import com.onesignal.common.AppContext
import com.onesignal.core.internal.features.FeatureManager
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.features.FeatureFlag
import com.onesignal.features.StartupFlagsSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ThreadPoolExecutor

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
    internal const val BASE_THREAD_NAME = "OneSignal" // Base thread name prefix
    private const val RELEASE_LOG_TAG = "OneSignal"

    @Volatile
    internal var beforeLaneCreateForTest: ((String) -> Unit)? = null

    @Volatile
    internal var afterExecutorCreateForTest: ((String, ThreadPoolExecutor) -> Unit)? = null

    @Volatile
    internal var beforeFallbackCreateForTest: ((String) -> Unit)? = null

    /** Lazy prewarm, before the lane lazy runs. Blocking here must not deadlock shutdown. */
    @Volatile
    internal var beforeLazyWarmForTest: ((String) -> Unit)? = null

    /** After the lazy prewarm thread is published, before [Thread.start]. */
    @Volatile
    internal var beforeLazyPrewarmStartForTest: (() -> Unit)? = null

    @Volatile
    internal var beforePoolChoiceForTest: (() -> Unit)? = null

    private val inFailureLog = ThreadLocal<Boolean>()

    @Volatile
    private var poolsRef: DispatcherPools? = null
    private val poolsLock = Any()

    /** Inside the choice report handoff, between reading readiness and parking the report. */
    @Volatile
    internal var duringChoiceHandoffForTest: (() -> Unit)? = null

    // Guards only the two fields below. Never report while holding it: reporting can launch.
    private val choiceReportLock = Any()
    private var telemetryReady = false
    private var pendingChoiceReport: PoolChoice? = null

    /** Test override. Null reads the cached flag, and no cache means off. */
    @Volatile
    internal var bootstrapEnabledForTest: Boolean? = null

    internal fun resetBootstrapInputsForTest() {
        AppContext.resetForTest()
        bootstrapEnabledForTest = null
        synchronized(choiceReportLock) {
            telemetryReady = false
            pendingChoiceReport = null
        }
        FeatureManager.resetStartupFlagsForTest()
    }

    private fun currentPools(): DispatcherPools {
        poolsRef?.let { return it }
        // Flag read does disk I/O. Keep it outside the lock so other launches are not stuck behind it.
        val choice = choosePoolsOrLazy()
        val published =
            synchronized(poolsLock) {
                val existing = poolsRef
                if (existing != null) {
                    choice.pools.shutdown()
                    existing
                } else {
                    poolsRef = choice.pools
                    choice.pools
                }
            }
        if (published === choice.pools) recordChoice(choice)
        return published
    }

    // Every launch in the process goes through this, so a failed choice must not throw.
    // Lazy pools need no flags, and they are what the flag being off already means.
    // A failure after an "on" snapshot leaves FeatureManager reporting the flag on over lazy
    // pools. The "choice failed" report is what tells those launches apart in telemetry.
    @Suppress("TooGenericExceptionCaught")
    private fun choosePoolsOrLazy(): PoolChoice =
        try {
            choosePools()
        } catch (t: Throwable) {
            PoolChoice(LazyDispatcherPools(), "off, choice failed", warn = true, error = t)
        }

    /** Call only after [choice] is published: reporting can log, and log listeners can launch. */
    private fun recordChoice(choice: PoolChoice) {
        if (bootstrapEnabledForTest != null) return
        logRelease(choice.summary, choice.error, warn = choice.warn)
        if (!choice.warn) return
        val reportNow =
            synchronized(choiceReportLock) {
                val ready = telemetryReady
                duringChoiceHandoffForTest?.invoke()
                if (!ready) pendingChoiceReport = choice
                ready
            }
        if (reportNow) reportChoice(choice)
    }

    /** Pools are usually chosen before init installs remote telemetry, so degraded choices wait for it. */
    internal fun reportChoiceToTelemetry() {
        val pending =
            synchronized(choiceReportLock) {
                telemetryReady = true
                pendingChoiceReport.also { pendingChoiceReport = null }
            }
        pending?.let(::reportChoice)
    }

    @Suppress("SwallowedException")
    private fun reportChoice(choice: PoolChoice) {
        try {
            Logging.warn("OneSignalDispatchers: sdk_dispatcher_bootstrap ${choice.summary}", choice.error)
        } catch (_: RuntimeException) {
            // A telemetry fault must not fail init or the launch that published the pools.
        }
    }

    private fun choosePools(): PoolChoice {
        beforePoolChoiceForTest?.invoke()
        val override = bootstrapEnabledForTest
        return if (override != null) {
            PoolChoice(poolsFor(override), "test", false)
        } else {
            chooseFromCache()
        }
    }

    private fun chooseFromCache(): PoolChoice {
        val flags = FeatureManager.startupFlags(AppContext::get)
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

    private class PoolChoice(
        val pools: DispatcherPools,
        val detail: String,
        val warn: Boolean,
        val error: Throwable? = null,
    ) {
        val summary: String get() = "$detail; pools=${if (pools is LazyDispatcherPools) "lazy" else "bootstrap"}"
    }

    /** Logcat at the customer's SDK log level, not through [Logging]: Logging can dispatch. */
    @Suppress("SwallowedException")
    internal fun logRelease(
        message: String,
        error: Throwable? = null,
        warn: Boolean = error != null,
    ) {
        val line = "sdk_dispatcher_bootstrap: $message"
        try {
            if (!Logging.atLogLevel(if (warn) LogLevel.WARN else LogLevel.INFO)) return
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
     * Notes [context] first, since the pools latch from the startup flags file, then warms every lane off
     * the caller when it can. Bootstrap queues construction. Lazy construction runs on the caller, or blocks behind prewarm.
     */
    @Suppress("TooGenericExceptionCaught")
    fun prewarm(context: Context?) {
        context?.let(AppContext::note)
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
        val choice = choosePoolsOrLazy()
        val old: DispatcherPools?
        synchronized(poolsLock) {
            old = poolsRef
            poolsRef = choice.pools
        }
        recordChoice(choice)
        try {
            old?.shutdown()
        } catch (e: Exception) {
            Logging.error("OneSignalDispatchers.resetForTest failed: ${e.message}", e)
        }
        resetPrewarmForTest()
        beforeLaneCreateForTest = null
        afterExecutorCreateForTest = null
        beforeFallbackCreateForTest = null
        beforeLazyWarmForTest = null
        beforeLazyPrewarmStartForTest = null
        beforePoolChoiceForTest = null
        duringChoiceHandoffForTest = null
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
            if (listOf(current.io, current.defaultLane, current.serialIO, current.ingress).all { it.status() in READY_STATUSES }) {
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

    private val READY_STATUSES = setOf("Active", "Fallback")
    private const val NOT_INITIALIZED = "Not initialized"
    private const val UNKNOWN_ERROR = "Unknown error"
}
