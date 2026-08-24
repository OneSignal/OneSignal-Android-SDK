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
import com.onesignal.debug.internal.logging.logger.android.AndroidLogCrashHandler
import com.onesignal.logger.ILogAnrDetector
import com.onesignal.logger.ILogFileStore
import com.onesignal.logger.ILogTelemetryRemote
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.robolectric.annotation.Config

/**
 * The logger pipeline is the SDK's only observability path, so these cover the config
 * state machine that brings it up and tears it down.
 */
@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class LoggerLifecycleManagerTest : FunSpec({
    lateinit var context: Context
    lateinit var featureManager: IFeatureManager
    var originalHandler: Thread.UncaughtExceptionHandler? = null

    fun newManager(): LoggerLifecycleManager =
        LoggerLifecycleManager(context = context, featureManagerProvider = { featureManager })

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
        // Logging holds the sink in a global; leaving one attached would leak into other specs.
        Logging.setLoggerTelemetry(null) { false }
    }

    test("initializeFromCachedConfig is a no-op when the SDK level is unsupported") {
        ObservabilitySdkSupport.isSupported = false

        newManager().initializeFromCachedConfig()

        Thread.getDefaultUncaughtExceptionHandler() shouldBe originalHandler
    }

    test("initializeFromCachedConfig with no cached config leaves features off") {
        newManager().initializeFromCachedConfig()

        Thread.getDefaultUncaughtExceptionHandler() shouldBe originalHandler
    }

    test("a HYDRATE with remote logging enabled installs the crash handler") {
        val manager = newManager()
        manager.initializeFromCachedConfig()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)

        Thread.getDefaultUncaughtExceptionHandler().shouldBeInstanceOf<AndroidLogCrashHandler>()
    }

    test("onModelReplaced is ignored when the SDK level is unsupported") {
        ObservabilitySdkSupport.isSupported = false
        val manager = newManager()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)

        Thread.getDefaultUncaughtExceptionHandler() shouldBe originalHandler
    }

    test("onModelReplaced ignores non-HYDRATE tags") {
        val manager = newManager()
        manager.initializeFromCachedConfig()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.NORMAL)

        Thread.getDefaultUncaughtExceptionHandler() shouldBe originalHandler
    }

    test("disabling remotely unregisters the crash handler") {
        val manager = newManager()
        manager.initializeFromCachedConfig()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = false, logLevel = null), ModelChangeTags.HYDRATE)

        Thread.getDefaultUncaughtExceptionHandler() shouldBe originalHandler
    }

    test("full lifecycle: enable, change level, disable, re-enable") {
        val manager = newManager()
        manager.initializeFromCachedConfig()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.WARN), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.INFO), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = false, logLevel = null), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.DEBUG), ModelChangeTags.HYDRATE)

        Thread.getDefaultUncaughtExceptionHandler().shouldBeInstanceOf<AndroidLogCrashHandler>()
    }

    test("repeating the same config does not re-register the handler") {
        val manager = newManager()
        manager.initializeFromCachedConfig()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        val afterFirst = Thread.getDefaultUncaughtExceptionHandler()
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)

        Thread.getDefaultUncaughtExceptionHandler() shouldBe afterFirst
    }

    // The ANR watchdog and the remote sink are not observable through the process-global
    // uncaught-exception handler, so these drive them through the injected factories.

    test("enabling starts the ANR detector and disabling stops it") {
        val detector = mockk<ILogAnrDetector>(relaxed = true)
        val manager =
            LoggerLifecycleManager(
                context = context,
                featureManagerProvider = { featureManager },
                platformProviderFactory = { _, _ -> mockk(relaxed = true) },
                logger = mockk(relaxed = true),
                fileStoreFactory = { mockk<ILogFileStore>(relaxed = true) },
                crashHandlerFactory = { _, _, _ -> mockk(relaxed = true) },
                anrDetectorFactory = { _, _, _ -> detector },
                remoteTelemetryFactory = { _, _ -> mockk(relaxed = true) },
            )

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        verify { detector.start() }

        manager.onModelReplaced(configWith(isEnabled = false, logLevel = null), ModelChangeTags.HYDRATE)
        verify { detector.stop() }
    }

    test("enabling wires the remote sink and disabling shuts it down and clears it") {
        val telemetry = mockk<ILogTelemetryRemote>(relaxed = true)
        val manager =
            LoggerLifecycleManager(
                context = context,
                featureManagerProvider = { featureManager },
                platformProviderFactory = { _, _ -> mockk(relaxed = true) },
                logger = mockk(relaxed = true),
                fileStoreFactory = { mockk<ILogFileStore>(relaxed = true) },
                crashHandlerFactory = { _, _, _ -> mockk(relaxed = true) },
                anrDetectorFactory = { _, _, _ -> mockk(relaxed = true) },
                remoteTelemetryFactory = { _, _ -> telemetry },
            )

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        // Sink is live: an ERROR at the configured level reaches the telemetry.
        Logging.error("routed while enabled")
        runBlocking { delay(SINK_SETTLE_MS) }
        coVerify { telemetry.emit(any()) }

        manager.onModelReplaced(configWith(isEnabled = false, logLevel = null), ModelChangeTags.HYDRATE)
        verify { telemetry.shutdown() }

        // Sink is detached: nothing further reaches it.
        clearMocks(telemetry, answers = false)
        Logging.error("dropped after disable")
        runBlocking { delay(SINK_SETTLE_MS) }
        coVerify(exactly = 0) { telemetry.emit(any()) }
    }
})

/** Remote emission hops to a background scope; long enough to settle without being flaky. */
private const val SINK_SETTLE_MS = 300L

private fun configWith(isEnabled: Boolean, logLevel: LogLevel?): ConfigModel {
    val config = ConfigModel()
    config.remoteLoggingParams.isEnabled = isEnabled
    logLevel?.let { config.remoteLoggingParams.logLevel = it }
    return config
}
