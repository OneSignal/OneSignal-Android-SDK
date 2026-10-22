package com.onesignal.common.threading

import android.content.Context
import android.util.Log
import com.onesignal.common.AppContext
import com.onesignal.core.internal.features.FeatureManager
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The SDK's coroutine lanes. Pools are chosen once per process from the startup flags:
 * bootstrap builds executors off the caller, lazy builds them on first use.
 */
@Suppress("TooManyFunctions")
object OneSignalDispatchers {
    private const val RELEASE_LOG_TAG = "OneSignal"

    private val inFailureLog = ThreadLocal<Boolean>()

    @Volatile
    private var poolsRef: DispatcherPools? = null
    private val poolsLock = Any()

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

    private fun currentPools(): DispatcherPools = poolsRef ?: publishFirstPools()

    // A launch from the choice re-enters here (poolsLock is reentrant) and publishes first; keep those pools.
    // Only test hooks re-enter today: the production choice reads a prefs file and builds pools without launching.
    // Logging can launch, so only the outermost call records, after publishing and outside poolsLock.
    private fun publishFirstPools(): DispatcherPools {
        var published: DispatcherPools? = null
        val choice =
            synchronized(poolsLock) {
                poolsRef?.let { return it }
                choosePools(bootstrapEnabledForTest, AppContext::get).also { choice ->
                    published = poolsRef
                    if (published != null) {
                        choice.pools.shutdown()
                    } else {
                        // Before publishing: below API 24 this mark is the origin every other mark is measured from.
                        StartupTimeline.poolsChosen(choice.mode, choice.detail)
                        poolsRef = choice.pools
                    }
                }
            }
        if (!Thread.holdsLock(poolsLock)) recordChoice(choice)
        return published ?: choice.pools
    }

    internal fun holdsPoolsLockForTest(): Boolean = Thread.holdsLock(poolsLock)

    internal fun getPerformanceMetrics(): String = performanceMetrics(currentPools())

    internal fun getStatus(): String = dispatcherStatus(currentPools())

    internal fun awaitReadyForTest(): Boolean = awaitPoolsReady(::currentPools)

    /** Call only after [choice] is published: reporting can log, and log listeners can launch. */
    private fun recordChoice(choice: PoolChoice) {
        if (bootstrapEnabledForTest != null) return
        logRelease(choice.summary, choice.failure, warn = choice.warn)
        if (!choice.warn) return
        val reportNow =
            synchronized(choiceReportLock) {
                val ready = telemetryReady
                DispatcherTestHooks.duringChoiceHandoff?.invoke()
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
            Logging.warn("OneSignalDispatchers: sdk_dispatcher_bootstrap ${choice.summary}", choice.failure)
        } catch (_: RuntimeException) {
            // A telemetry fault must not fail init or the launch that published the pools.
        }
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

    internal fun resetPrewarmForTest() {
        synchronized(prewarmLock) {
            prewarmStarted = false
        }
    }

    /**
     * Swaps in a fresh pool generation and `shutdownNow()`s the old one, so a worker a spec left
     * parked in a blocking wait cannot starve the small pools for later specs. Clears test hooks.
     */
    @Suppress("TooGenericExceptionCaught")
    internal fun resetForTest() {
        FeatureManager.resetStartupFlagsForTest()
        val choice = choosePools(bootstrapEnabledForTest, AppContext::get)
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
        DispatcherTestHooks.reset()
    }

    /** Back to the first-use state, which [resetForTest] never leaves, so a spec can drive the first choice. */
    internal fun unpublishPoolsForTest() {
        val old = synchronized(poolsLock) { poolsRef.also { poolsRef = null } }
        old?.shutdown()
    }
}
