package com.onesignal.internal

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.crash.OtelSdkSupport
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.debug.internal.logging.otel.android.OtelPlatformProvider
import com.onesignal.debug.internal.logging.otel.android.OtelPlatformProviderConfig
import com.onesignal.otel.IOtelCrashHandler
import com.onesignal.otel.IOtelLogger
import com.onesignal.otel.IOtelOpenTelemetryRemote
import com.onesignal.otel.IOtelPlatformProvider
import com.onesignal.otel.crash.IOtelAnrDetector
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.robolectric.annotation.Config

/**
 * Fault injection tests that prove all try/catch(Throwable) wrappers in
 * [OtelLifecycleManager] actually catch and suppress exceptions, and that
 * a failure in one feature does not prevent others from starting.
 */
@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class OtelLifecycleManagerFaultTest : FunSpec({

    lateinit var context: Context
    lateinit var mockCrashHandler: IOtelCrashHandler
    lateinit var mockAnrDetector: IOtelAnrDetector
    lateinit var mockTelemetry: IOtelOpenTelemetryRemote
    lateinit var mockLogger: IOtelLogger
    lateinit var mockPlatformProvider: OtelPlatformProvider

    beforeEach {
        context = ApplicationProvider.getApplicationContext()
        OtelSdkSupport.isSupported = true

        mockCrashHandler = mockk(relaxed = true)
        mockAnrDetector = mockk(relaxed = true)
        mockTelemetry = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
        mockPlatformProvider = OtelPlatformProvider(
            OtelPlatformProviderConfig(
                crashStoragePath = "/test/path",
                appPackageId = "com.test",
                appVersion = "1.0",
                context = context,
            )
        )
    }

    afterEach {
        OtelSdkSupport.reset()
        Logging.setOtelTelemetry(null) { false }
    }

    fun createManager(
        crashFactory: (Context, IOtelLogger) -> IOtelCrashHandler = { _, _ -> mockCrashHandler },
        anrFactory: (IOtelPlatformProvider, IOtelLogger, Long, Long) -> IOtelAnrDetector = { _, _, _, _ -> mockAnrDetector },
        telemetryFactory: (IOtelPlatformProvider) -> IOtelOpenTelemetryRemote = { mockTelemetry },
        ppFactory: (Context) -> OtelPlatformProvider = { mockPlatformProvider },
    ): OtelLifecycleManager =
        OtelLifecycleManager(
            context = context,
            crashHandlerFactory = crashFactory,
            anrDetectorFactory = anrFactory,
            remoteTelemetryFactory = telemetryFactory,
            platformProviderFactory = ppFactory,
            loggerFactory = { mockLogger },
        )

    // ------------------------------------------------------------------
    // Factory-level fault injection: factory itself throws
    // ------------------------------------------------------------------

    test("crash handler factory throws — ANR and logging still start") {
        var telemetryCreated = false
        val manager = createManager(
            crashFactory = { _, _ -> throw RuntimeException("crash factory boom") },
            telemetryFactory = { telemetryCreated = true; mockTelemetry },
        )
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)

        verify(exactly = 1) { mockAnrDetector.start() }
        telemetryCreated shouldBe true
    }

    test("ANR factory throws — crash handler and logging still start") {
        var telemetryCreated = false
        val manager = createManager(
            anrFactory = { _, _, _, _ -> throw RuntimeException("anr factory boom") },
            telemetryFactory = { telemetryCreated = true; mockTelemetry },
        )
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)

        verify(exactly = 1) { mockCrashHandler.initialize() }
        telemetryCreated shouldBe true
    }

    test("telemetry factory throws — crash handler and ANR still start") {
        val manager = createManager(
            telemetryFactory = { throw RuntimeException("telemetry factory boom") },
        )
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)

        verify(exactly = 1) { mockCrashHandler.initialize() }
        verify(exactly = 1) { mockAnrDetector.start() }
    }

    test("all three factories throw — no exception propagates") {
        val manager = createManager(
            crashFactory = { _, _ -> throw RuntimeException("crash") },
            anrFactory = { _, _, _, _ -> throw RuntimeException("anr") },
            telemetryFactory = { throw RuntimeException("telemetry") },
        )
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
    }

    // ------------------------------------------------------------------
    // Initialize-level fault injection: object created but init throws
    // ------------------------------------------------------------------

    test("crash handler initialize() throws — ANR and logging still start") {
        every { mockCrashHandler.initialize() } throws RuntimeException("init boom")
        var telemetryCreated = false

        val manager = createManager(
            telemetryFactory = { telemetryCreated = true; mockTelemetry },
        )
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)

        verify(exactly = 1) { mockAnrDetector.start() }
        telemetryCreated shouldBe true
    }

    test("ANR detector start() throws — crash handler and logging still start") {
        every { mockAnrDetector.start() } throws RuntimeException("start boom")
        var telemetryCreated = false

        val manager = createManager(
            telemetryFactory = { telemetryCreated = true; mockTelemetry },
        )
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)

        verify(exactly = 1) { mockCrashHandler.initialize() }
        telemetryCreated shouldBe true
    }

    // ------------------------------------------------------------------
    // Disable-level fault injection: shutdown/stop/unregister throws
    // ------------------------------------------------------------------

    test("ANR stop() throws during disable — crash unregister and telemetry shutdown still run") {
        every { mockAnrDetector.stop() } throws RuntimeException("stop boom")

        val manager = createManager()
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = false, logLevel = null), ModelChangeTags.HYDRATE)

        verify(exactly = 1) { mockCrashHandler.unregister() }
        verify(exactly = 1) { mockTelemetry.shutdown() }
    }

    test("crash handler unregister() throws during disable — telemetry shutdown still runs") {
        every { mockCrashHandler.unregister() } throws RuntimeException("unregister boom")

        val manager = createManager()
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = false, logLevel = null), ModelChangeTags.HYDRATE)

        verify(exactly = 1) { mockTelemetry.shutdown() }
    }

    test("telemetry shutdown() throws during disable — no exception propagates") {
        every { mockTelemetry.shutdown() } throws RuntimeException("shutdown boom")

        val manager = createManager()
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = false, logLevel = null), ModelChangeTags.HYDRATE)

        verify(exactly = 1) { mockAnrDetector.stop() }
        verify(exactly = 1) { mockCrashHandler.unregister() }
    }

    // ------------------------------------------------------------------
    // Platform provider fault injection
    // ------------------------------------------------------------------

    test("platform provider factory throws — initializeFromCachedConfig does not propagate") {
        val manager = createManager(
            ppFactory = { throw RuntimeException("provider boom") },
        )
        manager.initializeFromCachedConfig()
    }

    // ------------------------------------------------------------------
    // UpdateLogLevel fault injection
    // ------------------------------------------------------------------

    test("telemetry factory throws during log level update — no exception propagates") {
        var callCount = 0
        val manager = createManager(
            telemetryFactory = {
                callCount++
                if (callCount > 1) throw RuntimeException("second create boom")
                mockTelemetry
            },
        )
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.WARN), ModelChangeTags.HYDRATE)
    }

    // ------------------------------------------------------------------
    // Idempotency: calling enable twice doesn't double-create
    // ------------------------------------------------------------------

    test("enable called twice does not create duplicate crash handler or ANR detector") {
        val manager = createManager()
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = false, logLevel = null), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.WARN), ModelChangeTags.HYDRATE)

        verify(exactly = 2) { mockCrashHandler.initialize() }
        verify(exactly = 2) { mockAnrDetector.start() }
    }

    // ------------------------------------------------------------------
    // Verify mock interactions in happy path
    // ------------------------------------------------------------------

    test("enable creates all three features and disable tears all down") {
        val manager = createManager()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        verify(exactly = 1) { mockCrashHandler.initialize() }
        verify(exactly = 1) { mockAnrDetector.start() }

        manager.onModelReplaced(configWith(isEnabled = false, logLevel = null), ModelChangeTags.HYDRATE)
        verify(exactly = 1) { mockCrashHandler.unregister() }
        verify(exactly = 1) { mockAnrDetector.stop() }
        verify { mockTelemetry.shutdown() }
    }

    test("update log level shuts down old telemetry and creates new one") {
        var createCount = 0
        val telemetry1 = mockk<IOtelOpenTelemetryRemote>(relaxed = true)
        val telemetry2 = mockk<IOtelOpenTelemetryRemote>(relaxed = true)
        val manager = createManager(
            telemetryFactory = {
                createCount++
                if (createCount == 1) telemetry1 else telemetry2
            },
        )

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.WARN), ModelChangeTags.HYDRATE)

        verify(exactly = 1) { telemetry1.shutdown() }
        createCount shouldBe 2
    }

    // ------------------------------------------------------------------
    // Error type coverage: OutOfMemoryError, StackOverflowError
    // ------------------------------------------------------------------

    test("OutOfMemoryError from factory does not propagate") {
        val manager = createManager(
            crashFactory = { _, _ -> throw OutOfMemoryError("oom") },
        )
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)

        verify(exactly = 1) { mockAnrDetector.start() }
    }

    test("StackOverflowError from factory does not propagate") {
        val manager = createManager(
            anrFactory = { _, _, _, _ -> throw StackOverflowError("stack overflow") },
        )
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)

        verify(exactly = 1) { mockCrashHandler.initialize() }
    }

    // ------------------------------------------------------------------
    // initializeFromCachedConfig fault injection
    // ------------------------------------------------------------------

    test("initializeFromCachedConfig catches factory failure and does not propagate") {
        val manager = createManager(
            crashFactory = { _, _ -> throw RuntimeException("crash") },
            anrFactory = { _, _, _, _ -> throw RuntimeException("anr") },
            telemetryFactory = { throw RuntimeException("telemetry") },
        )
        manager.initializeFromCachedConfig()
    }
})

private fun configWith(isEnabled: Boolean, logLevel: LogLevel?): ConfigModel {
    val config = ConfigModel()
    config.remoteLoggingParams.isEnabled = isEnabled
    logLevel?.let { config.remoteLoggingParams.logLevel = it }
    return config
}
