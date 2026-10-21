package com.onesignal.common.threading

import android.content.Context
import android.util.Log
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

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var telemetryReady = false

    @Volatile
    private var pendingChoiceReport: PoolChoice? = null

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
        telemetryReady = false
        pendingChoiceReport = null
        FeatureManager.resetStartupFlagsForTest()
    }

    internal fun currentPools(): DispatcherPools = poolsRef ?: publishFirstPools()

    // The choice must not launch work: a launch would re-enter here before poolsRef is set.
    // Logging can launch, so a choice failure is logged only after publishing.
    private fun publishFirstPools(): DispatcherPools {
        val choice =
            synchronized(poolsLock) {
                poolsRef?.let { return it }
                choosePools(bootstrapEnabledForTest, ::applicationContext).also { poolsRef = it.pools }
            }
        recordChoice(choice)
        return choice.pools
    }

    /** Call only after [choice] is published: reporting can log, and log listeners can launch. */
    private fun recordChoice(choice: PoolChoice) {
        if (bootstrapEnabledForTest != null) return
        logRelease(choice.summary, choice.failure, warn = choice.warn)
        if (!choice.warn) return
        if (telemetryReady) reportChoice(choice) else pendingChoiceReport = choice
    }

    /** Pools are usually chosen before init installs remote telemetry, so degraded choices wait for it. */
    internal fun reportChoiceToTelemetry() {
        telemetryReady = true
        pendingChoiceReport?.let(::reportChoice)
        pendingChoiceReport = null
    }

    @Suppress("SwallowedException")
    private fun reportChoice(choice: PoolChoice) {
        try {
            Logging.warn("OneSignalDispatchers: sdk_dispatcher_bootstrap ${choice.summary}", choice.failure)
        } catch (_: RuntimeException) {
            // A telemetry fault must not fail init or the launch that published the pools.
        }
    }

    private fun applicationContext(): Context? {
        if (appContext == null) currentApplication()?.let { appContext = it.applicationContext }
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
        val choice = choosePools(bootstrapEnabledForTest, ::applicationContext)
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
