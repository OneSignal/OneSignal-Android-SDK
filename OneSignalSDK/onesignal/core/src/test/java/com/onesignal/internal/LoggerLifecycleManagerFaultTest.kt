package com.onesignal.internal

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.crash.ObservabilitySdkSupport
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.logger.ILogAnrDetector
import com.onesignal.logger.ILogCrashHandler
import com.onesignal.logger.ILogFileStore
import com.onesignal.logger.ILogTelemetryRemote
import com.onesignal.logger.ILogger
import com.onesignal.logger.ILoggerPlatformProvider
import com.onesignal.logger.IObservabilityEventRecorder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.robolectric.annotation.Config

/**
 * Fault isolation for [LoggerLifecycleManager]: one failing component must not stop the others,
 * and nothing may reach the caller — this runs inside SDK init, where a throw kills the host app.
 */
@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class LoggerLifecycleManagerFaultTest : FunSpec({
    lateinit var context: Context
    lateinit var featureManager: IFeatureManager
    var originalHandler: Thread.UncaughtExceptionHandler? = null

    beforeEach {
        context = ApplicationProvider.getApplicationContext()
        featureManager = mockk<IFeatureManager>().also {
            every { it.enabledFeatureKeys() } returns emptyList()
        }
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        ObservabilitySdkSupport.isSupported = true
    }

    afterEach {
        ObservabilitySdkSupport.reset()
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        // The sink lives in a process-global; leaving one attached leaks into later specs.
        Logging.setLoggerTelemetry(null) { false }
    }

    fun enabledConfig(logLevel: LogLevel = LogLevel.ERROR): ConfigModel =
        ConfigModel().apply {
            remoteLoggingParams.isEnabled = true
            remoteLoggingParams.logLevel = logLevel
        }

    fun disabledConfig(): ConfigModel =
        ConfigModel().apply { remoteLoggingParams.isEnabled = false }

    /** Collaborators are all relaxed mocks unless a factory is overridden to throw. */
    fun managerWith(
        crashHandler: () -> ILogCrashHandler = { mockk(relaxed = true) },
        anrDetector: () -> ILogAnrDetector = { mockk(relaxed = true) },
        remoteTelemetry: () -> ILogTelemetryRemote = { mockk(relaxed = true) },
        platformProvider: () -> ILoggerPlatformProvider = { mockk(relaxed = true) },
    ): LoggerLifecycleManager =
        LoggerLifecycleManager(
            context = context,
            featureManagerProvider = { featureManager },
            platformProviderFactory = { _, _ -> platformProvider() },
            logger = mockk<ILogger>(relaxed = true),
            fileStoreFactory = { mockk<ILogFileStore>(relaxed = true) },
            crashHandlerFactory = { _, _, _ -> crashHandler() },
            anrDetectorFactory = { _, _, _ -> anrDetector() },
            remoteTelemetryFactory = { _, _ -> remoteTelemetry() },
        )

    // ===== One failing collaborator must not block the others =====

    test("crash handler factory throws — ANR and logging still start") {
        val detector = mockk<ILogAnrDetector>(relaxed = true)
        val manager = managerWith(
            crashHandler = { throw RuntimeException("crash handler factory boom") },
            anrDetector = { detector },
        )

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        verify { detector.start() }
    }

    test("ANR factory throws — crash handler and logging still start") {
        val handler = mockk<ILogCrashHandler>(relaxed = true)
        val manager = managerWith(
            crashHandler = { handler },
            anrDetector = { throw RuntimeException("anr factory boom") },
        )

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        verify { handler.initialize() }
    }

    test("telemetry factory throws — crash handler and ANR still start") {
        val handler = mockk<ILogCrashHandler>(relaxed = true)
        val detector = mockk<ILogAnrDetector>(relaxed = true)
        val manager = managerWith(
            crashHandler = { handler },
            anrDetector = { detector },
            remoteTelemetry = { throw RuntimeException("telemetry factory boom") },
        )

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        verify { handler.initialize() }
        verify { detector.start() }
    }

    test("all three factories throw — no exception propagates") {
        val manager = managerWith(
            crashHandler = { throw RuntimeException("a") },
            anrDetector = { throw RuntimeException("b") },
            remoteTelemetry = { throw RuntimeException("c") },
        )

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)
    }

    // ===== Collaborator methods throwing on the way up =====

    test("crash handler initialize() throws — ANR and logging still start") {
        val handler = mockk<ILogCrashHandler>(relaxed = true)
        every { handler.initialize() } throws RuntimeException("initialize boom")
        val detector = mockk<ILogAnrDetector>(relaxed = true)
        val manager = managerWith(crashHandler = { handler }, anrDetector = { detector })

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        verify { detector.start() }
    }

    test("ANR detector start() throws — crash handler and logging still start") {
        val handler = mockk<ILogCrashHandler>(relaxed = true)
        val detector = mockk<ILogAnrDetector>(relaxed = true)
        every { detector.start() } throws RuntimeException("start boom")
        val manager = managerWith(crashHandler = { handler }, anrDetector = { detector })

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        verify { handler.initialize() }
    }

    // ===== Collaborator methods throwing on the way down =====

    test("ANR stop() throws during disable — crash unregister and telemetry shutdown still run") {
        val handler = mockk<ILogCrashHandler>(relaxed = true)
        val detector = mockk<ILogAnrDetector>(relaxed = true)
        every { detector.stop() } throws RuntimeException("stop boom")
        val telemetry = mockk<ILogTelemetryRemote>(relaxed = true)
        val manager = managerWith(
            crashHandler = { handler },
            anrDetector = { detector },
            remoteTelemetry = { telemetry },
        )
        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        manager.onModelReplaced(disabledConfig(), ModelChangeTags.HYDRATE)

        verify { handler.unregister() }
        verify { telemetry.shutdown() }
    }

    test("crash handler unregister() throws during disable — telemetry shutdown still runs") {
        val handler = mockk<ILogCrashHandler>(relaxed = true)
        every { handler.unregister() } throws RuntimeException("unregister boom")
        val telemetry = mockk<ILogTelemetryRemote>(relaxed = true)
        val manager = managerWith(crashHandler = { handler }, remoteTelemetry = { telemetry })
        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        manager.onModelReplaced(disabledConfig(), ModelChangeTags.HYDRATE)

        verify { telemetry.shutdown() }
    }

    test("telemetry shutdown() throws during disable — no exception propagates") {
        val telemetry = mockk<ILogTelemetryRemote>(relaxed = true)
        every { telemetry.shutdown() } throws RuntimeException("shutdown boom")
        val manager = managerWith(remoteTelemetry = { telemetry })
        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        manager.onModelReplaced(disabledConfig(), ModelChangeTags.HYDRATE)
    }

    // ===== Cold-start and update paths =====

    test("platform provider factory throws — initializeFromCachedConfig does not propagate") {
        val manager = managerWith(
            platformProvider = { throw RuntimeException("provider boom") },
        )

        manager.initializeFromCachedConfig()
    }

    test("a throwing shutdown() during a level change still installs the replacement sink") {
        // Clearing after teardown would strand the dead instance and keep logging down.
        val failing = mockk<ILogTelemetryRemote>(relaxed = true)
        every { failing.shutdown() } throws RuntimeException("shutdown boom")
        val replacement = mockk<ILogTelemetryRemote>(relaxed = true)
        var calls = 0
        val manager = managerWith(remoteTelemetry = { if (calls++ == 0) failing else replacement })

        manager.onModelReplaced(enabledConfig(LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(enabledConfig(LogLevel.WARN), ModelChangeTags.HYDRATE)

        calls shouldBe 2
        manager.onModelReplaced(disabledConfig(), ModelChangeTags.HYDRATE)
        verify { replacement.shutdown() }
    }

    test("telemetry factory throws during log level update — no exception propagates") {
        var calls = 0
        val manager = managerWith(
            remoteTelemetry = {
                calls++
                if (calls == 1) mockk(relaxed = true) else throw RuntimeException("update boom")
            },
        )
        manager.onModelReplaced(enabledConfig(LogLevel.ERROR), ModelChangeTags.HYDRATE)

        manager.onModelReplaced(enabledConfig(LogLevel.WARN), ModelChangeTags.HYDRATE)
    }

    // ===== Idempotency and full lifecycle =====

    test("a repeated identical config is a no-op and does not rebuild collaborators") {
        var handlerCount = 0
        var detectorCount = 0
        val manager = managerWith(
            crashHandler = { handlerCount++; mockk(relaxed = true) },
            anrDetector = { detectorCount++; mockk(relaxed = true) },
        )

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        // The second HYDRATE evaluates to NoChange, so enableFeatures is never re-entered.
        handlerCount shouldBe 1
        detectorCount shouldBe 1
    }

    // Committing currentConfig after a partial failure collapses the next HYDRATE to NoChange.

    test("a crash handler that failed to start is retried on the next identical config") {
        val failing = mockk<ILogCrashHandler>(relaxed = true)
        every { failing.initialize() } throws RuntimeException("initialize boom")
        val replacement = mockk<ILogCrashHandler>(relaxed = true)
        var calls = 0
        val manager = managerWith(crashHandler = { if (calls++ == 0) failing else replacement })

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        verify { replacement.initialize() }
    }

    test("an ANR detector that failed to start is retried on the next identical config") {
        val failing = mockk<ILogAnrDetector>(relaxed = true)
        every { failing.start() } throws RuntimeException("start boom")
        val replacement = mockk<ILogAnrDetector>(relaxed = true)
        var calls = 0
        val manager = managerWith(anrDetector = { if (calls++ == 0) failing else replacement })

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        verify { replacement.start() }
    }

    test("a retry does not tear down the components that did start") {
        val handler = mockk<ILogCrashHandler>(relaxed = true)
        val failingDetector = mockk<ILogAnrDetector>(relaxed = true)
        every { failingDetector.start() } throws RuntimeException("start boom")
        var detectorCalls = 0
        var handlerCalls = 0
        val manager =
            managerWith(
                crashHandler = { handlerCalls++; handler },
                anrDetector = { detectorCalls++; if (detectorCalls == 1) failingDetector else mockk(relaxed = true) },
            )

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        // Only the ANR detector is rebuilt; the healthy crash handler is left alone.
        handlerCalls shouldBe 1
        detectorCalls shouldBe 2
    }

    test("once every component is up an identical config stops retrying") {
        var handlerCalls = 0
        val manager = managerWith(crashHandler = { handlerCalls++; mockk(relaxed = true) })

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        handlerCalls shouldBe 1
    }

    test("disable then re-enable builds fresh collaborators") {
        var handlerCount = 0
        var detectorCount = 0
        val manager = managerWith(
            crashHandler = { handlerCount++; mockk(relaxed = true) },
            anrDetector = { detectorCount++; mockk(relaxed = true) },
        )

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(disabledConfig(), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        handlerCount shouldBe 2
        detectorCount shouldBe 2
    }

    // Clear-before-teardown, so a throwing stop() cannot leave a dead component looking live.

    test("a throwing ANR stop() still allows the detector to restart on re-enable") {
        val failing = mockk<ILogAnrDetector>(relaxed = true)
        every { failing.stop() } throws RuntimeException("stop boom")
        val replacement = mockk<ILogAnrDetector>(relaxed = true)
        var calls = 0
        val manager = managerWith(anrDetector = { if (calls++ == 0) failing else replacement })

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(disabledConfig(), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        verify { replacement.start() }
    }

    test("a throwing crash-handler unregister() still allows the handler to restart on re-enable") {
        val failing = mockk<ILogCrashHandler>(relaxed = true)
        every { failing.unregister() } throws RuntimeException("unregister boom")
        val replacement = mockk<ILogCrashHandler>(relaxed = true)
        var calls = 0
        val manager = managerWith(crashHandler = { if (calls++ == 0) failing else replacement })

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(disabledConfig(), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        verify { replacement.initialize() }
    }

    // ===== Desired config vs. actual liveness =====
    // A partial-failure Enable leaves components live under an uncommitted config, so the
    // evaluator alone cannot decide these two.

    test("disable tears down live components after a partial-failure enable") {
        val detector = mockk<ILogAnrDetector>(relaxed = true)
        val telemetry = mockk<ILogTelemetryRemote>(relaxed = true)
        val manager = managerWith(
            crashHandler = { throw RuntimeException("crash handler factory boom") },
            anrDetector = { detector },
            remoteTelemetry = { telemetry },
        )
        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        manager.onModelReplaced(disabledConfig(), ModelChangeTags.HYDRATE)

        // currentConfig is still null, so a config-only diff reads null -> disabled as NoChange.
        verify { detector.stop() }
        verify { telemetry.shutdown() }
    }

    test("an enable retry moves a live sink to the newly requested level") {
        val failingDetector = mockk<ILogAnrDetector>(relaxed = true)
        every { failingDetector.start() } throws RuntimeException("start boom")
        var telemetryCalls = 0
        val manager = managerWith(
            anrDetector = { failingDetector },
            remoteTelemetry = { telemetryCalls++; mockk(relaxed = true) },
        )

        manager.onModelReplaced(enabledConfig(LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(enabledConfig(LogLevel.DEBUG), ModelChangeTags.HYDRATE)

        // Skipping startLogging on retry would commit DEBUG while the sink filters at ERROR.
        telemetryCalls shouldBe 2
    }

    // ===== Partial starts must be unwound, not abandoned =====

    test("a crash handler that throws after installing itself is unregistered") {
        val failing = mockk<ILogCrashHandler>(relaxed = true)
        every { failing.initialize() } throws RuntimeException("initialize boom")
        val manager = managerWith(crashHandler = { failing })

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        // initialize() can chain onto the global handler before throwing; the retry would chain
        // a second one and double-report.
        verify { failing.unregister() }
    }

    test("an ANR detector that throws after starting is stopped") {
        val failing = mockk<ILogAnrDetector>(relaxed = true)
        every { failing.start() } throws RuntimeException("start boom")
        val manager = managerWith(anrDetector = { failing })

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        verify { failing.stop() }
    }

    test("a failed log level update is retried when the same new level arrives again") {
        var calls = 0
        val manager = managerWith(
            remoteTelemetry = {
                calls++
                if (calls == 2) throw RuntimeException("update boom") else mockk(relaxed = true)
            },
        )

        manager.onModelReplaced(enabledConfig(LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(enabledConfig(LogLevel.WARN), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(enabledConfig(LogLevel.WARN), ModelChangeTags.HYDRATE)

        // The failed update never commits, so the repeat WARN is another UpdateLogLevel.
        calls shouldBe 3
    }

    // ===== A failed level change must not cost a sink that was already working =====
    // The retry above is the lucky path: one params fetch per session is the norm, so build the
    // replacement before giving up the incumbent.

    test("a failed level change leaves the previously working sink serving at the old level") {
        val emitted = CompletableDeferred<Unit>()
        val working = mockk<ILogTelemetryRemote>(relaxed = true)
        coEvery { working.emit(any()) } answers { emitted.complete(Unit); Unit }
        var calls = 0
        val manager = managerWith(
            remoteTelemetry = { if (calls++ == 0) working else throw RuntimeException("update boom") },
        )

        manager.onModelReplaced(enabledConfig(LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(enabledConfig(LogLevel.WARN), ModelChangeTags.HYDRATE)

        calls shouldBe 2
        verify(exactly = 0) { working.shutdown() }
        Logging.error("routed after the failed level change")
        runBlocking { withTimeout(SINK_TIMEOUT_MS) { emitted.await() } }
    }

    test("a HYDRATE back to the old level after a failed change neither loses nor rebuilds the sink") {
        val emitted = CompletableDeferred<Unit>()
        val working = mockk<ILogTelemetryRemote>(relaxed = true)
        coEvery { working.emit(any()) } answers { emitted.complete(Unit); Unit }
        var calls = 0
        val manager = managerWith(
            remoteTelemetry = {
                calls++
                if (calls == 1) working else if (calls == 2) throw RuntimeException("update boom") else mockk(relaxed = true)
            },
        )

        manager.onModelReplaced(enabledConfig(LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(enabledConfig(LogLevel.WARN), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(enabledConfig(LogLevel.ERROR), ModelChangeTags.HYDRATE)

        // NoChange against the uncommitted ERROR config, and nothing to repair.
        calls shouldBe 2
        verify(exactly = 0) { working.shutdown() }
        Logging.error("routed after the revert")
        runBlocking { withTimeout(SINK_TIMEOUT_MS) { emitted.await() } }
    }

    test("enable creates all three features and disable tears all down") {
        val handler = mockk<ILogCrashHandler>(relaxed = true)
        val detector = mockk<ILogAnrDetector>(relaxed = true)
        val telemetry = mockk<ILogTelemetryRemote>(relaxed = true)
        val manager = managerWith(
            crashHandler = { handler },
            anrDetector = { detector },
            remoteTelemetry = { telemetry },
        )

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)
        verify { handler.initialize() }
        verify { detector.start() }

        manager.onModelReplaced(disabledConfig(), ModelChangeTags.HYDRATE)
        verify { detector.stop() }
        verify { handler.unregister() }
        verify { telemetry.shutdown() }
    }

    test("update log level shuts down old telemetry and creates new one") {
        val first = mockk<ILogTelemetryRemote>(relaxed = true)
        val second = mockk<ILogTelemetryRemote>(relaxed = true)
        var calls = 0
        val manager = managerWith(remoteTelemetry = { if (calls++ == 0) first else second })

        manager.onModelReplaced(enabledConfig(LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(enabledConfig(LogLevel.WARN), ModelChangeTags.HYDRATE)

        verify { first.shutdown() }
        calls shouldBe 2
    }

    // ===== Errors, not just Exceptions =====
    // Pins the catch clauses to Throwable: narrowing to Exception would crash the host app.

    test("OutOfMemoryError from factory does not propagate") {
        val manager = managerWith(crashHandler = { throw OutOfMemoryError("oom") })

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)
    }

    test("StackOverflowError from factory does not propagate") {
        val manager = managerWith(anrDetector = { throw StackOverflowError("so") })

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)
    }

    test("initializeFromCachedConfig catches factory failure and does not propagate") {
        val provider = mockk<ILoggerPlatformProvider>(relaxed = true)
        every { provider.isRemoteLoggingEnabled } returns true
        every { provider.remoteLogLevel } returns "ERROR"
        val manager = managerWith(
            crashHandler = { throw RuntimeException("cold start boom") },
            platformProvider = { provider },
        )

        manager.initializeFromCachedConfig()
    }

    // ===== The event recorder cannot take the pipeline down =====
    // It rides the remote telemetry: a fault in it must not fail the level change, block the
    // teardown, or reach the init path that hands it over.

    test("event recorder attach and detach throw — the telemetry still comes up and is torn down") {
        val telemetry = mockk<ILogTelemetryRemote>(relaxed = true)
        val recorder = mockk<IObservabilityEventRecorder>()
        every { recorder.attach(any()) } throws RuntimeException("attach boom")
        every { recorder.detach(any()) } throws RuntimeException("detach boom")
        val manager = managerWith(remoteTelemetry = { telemetry })
        manager.attachEventRecorder(recorder)

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(disabledConfig(), ModelChangeTags.HYDRATE)

        verify { telemetry.shutdown() }
    }

    test("event recorder attach throws during a level change — the new telemetry is still adopted") {
        val first = mockk<ILogTelemetryRemote>(relaxed = true)
        val second = mockk<ILogTelemetryRemote>(relaxed = true)
        var calls = 0
        val recorder = mockk<IObservabilityEventRecorder>()
        every { recorder.attach(any()) } throws RuntimeException("attach boom")
        val manager = managerWith(remoteTelemetry = { if (calls++ == 0) first else second })
        manager.attachEventRecorder(recorder)

        manager.onModelReplaced(enabledConfig(LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(enabledConfig(LogLevel.WARN), ModelChangeTags.HYDRATE)

        verify { first.shutdown() }
        calls shouldBe 2
    }

    test("event recorder attach throws against live telemetry — attachEventRecorder does not propagate and the recorder is kept") {
        val recorder = mockk<IObservabilityEventRecorder>()
        every { recorder.attach(any()) } throws RuntimeException("attach boom")
        val manager = managerWith()
        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        manager.attachEventRecorder(recorder)

        // The fault must not have dropped the hand-over: the next level change still attaches.
        manager.onModelReplaced(enabledConfig(LogLevel.WARN), ModelChangeTags.HYDRATE)
        verify(exactly = 2) { recorder.attach(any()) }
    }

    test("an enable retry after a partial failure attaches the event recorder once") {
        var crashHandlerAttempts = 0
        val recorder = mockk<IObservabilityEventRecorder>(relaxed = true)
        val manager =
            managerWith(
                crashHandler = {
                    if (crashHandlerAttempts++ == 0) throw RuntimeException("first crash handler boom")
                    mockk(relaxed = true)
                },
            )
        manager.attachEventRecorder(recorder)

        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(enabledConfig(), ModelChangeTags.HYDRATE)

        crashHandlerAttempts shouldBe 2
        verify(exactly = 1) { recorder.attach(any()) }
    }
})

/** Generous upper bound on a signal we expect; only a hang burns the full budget. */
private const val SINK_TIMEOUT_MS = 5_000L
