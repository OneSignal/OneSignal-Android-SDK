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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.robolectric.annotation.Config

/**
 * Fault-isolation coverage for the SDK's only observability pipeline, ported from the
 * deleted otel equivalent.
 *
 * Every collaborator is constructed behind an injectable factory, so these drive the
 * `try/catch` isolation in [LoggerLifecycleManager] directly: one failing component must
 * never stop the others from starting, and nothing may propagate to the caller — the
 * lifecycle manager runs inside SDK init, where a throw would take down the host app.
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
        // Enabling installs a mock sink into the process-global Logging; leaving one
        // attached would leak into every later spec in this JVM.
        Logging.setLoggerTelemetry(null) { false }
    }

    fun enabledConfig(logLevel: LogLevel = LogLevel.ERROR): ConfigModel =
        ConfigModel().apply {
            remoteLoggingParams.isEnabled = true
            remoteLoggingParams.logLevel = logLevel
        }

    fun disabledConfig(): ConfigModel =
        ConfigModel().apply { remoteLoggingParams.isEnabled = false }

    /**
     * Builds a manager whose collaborators are all mocks unless a factory is overridden to
     * throw. The platform provider is relaxed so property reads during startup are inert.
     */
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
        // startLogging drops the reference before tearing the old sink down. If it cleared
        // after, a throwing shutdown() would strand the dead instance in the field and every
        // later identical config would evaluate to NoChange, leaving remote logging dead.
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

    // Teardown clears each reference before calling the collaborator. If it cleared after,
    // a throwing stop()/unregister() would leave the field set and the start guards would
    // treat the dead component as running, disabling it for the rest of the process.

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
    // The catch clauses are on Throwable; these pin that intent so a later narrowing to
    // Exception cannot silently make SDK init crash the host app.

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
})
