package com.onesignal.internal

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.robolectric.annotation.Config
import com.onesignal.core.internal.config.CONFIG_NAME_SPACE as configNameSpace

/** Covers the config state machine that brings the logger pipeline up and tears it down. */
@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class LoggerLifecycleManagerTest : FunSpec({
    lateinit var context: Context
    lateinit var featureManager: IFeatureManager
    var originalHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * Real provider and crash handler, so assertions see genuine handler registration. The real
     * ANR detector must stay stubbed: its daemon thread outlives the spec and writes to the cache.
     */
    fun newManager(
        anrDetector: ILogAnrDetector = mockk(relaxed = true),
        remoteTelemetry: ILogTelemetryRemote = mockk(relaxed = true),
    ): LoggerLifecycleManager =
        LoggerLifecycleManager(
            context = context,
            featureManagerProvider = { featureManager },
            fileStoreFactory = { mockk<ILogFileStore>(relaxed = true) },
            anrDetectorFactory = { _, _, _ -> anrDetector },
            remoteTelemetryFactory = { _, _ -> remoteTelemetry },
        )

    /** Seeds the cache the real platform provider reads on cold start, before any HYDRATE. */
    fun writeCachedLoggingParams(remoteLoggingParams: String) {
        val configModel =
            JSONObject().put(ConfigModel::remoteLoggingParams.name, JSONObject(remoteLoggingParams))
        context.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
            .edit()
            .putString(
                PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace,
                JSONArray().put(configModel).toString(),
            )
            .commit()
    }

    beforeEach {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE).edit().clear().commit()
        featureManager = mockk<IFeatureManager>().also {
            every { it.enabledFeatureKeys() } returns emptyList()
        }
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        ObservabilitySdkSupport.isSupported = true
    }

    afterEach {
        ObservabilitySdkSupport.reset()
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        context.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE).edit().clear().commit()
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

    // ===== Cold start reads the cache, and the cache can disagree with itself =====
    // A server disable rewrites only isEnabled, so a stale level survives beside it. Cold start
    // runs before any params fetch, and that fetch may never succeed.

    test("initializeFromCachedConfig honors a cached kill switch beside a stale level") {
        writeCachedLoggingParams("""{"logLevel":"ERROR","isEnabled":false}""")
        val detector = mockk<ILogAnrDetector>(relaxed = true)
        val telemetry = mockk<ILogTelemetryRemote>(relaxed = true)

        newManager(anrDetector = detector, remoteTelemetry = telemetry).initializeFromCachedConfig()

        Thread.getDefaultUncaughtExceptionHandler() shouldBe originalHandler
        verify(exactly = 0) { detector.start() }
        Logging.error("dropped while the kill switch is on")
        runBlocking { delay(SINK_QUIET_MS) }
        coVerify(exactly = 0) { telemetry.emit(any()) }
    }

    test("initializeFromCachedConfig starts the pipeline when the cache says enabled") {
        writeCachedLoggingParams("""{"logLevel":"ERROR","isEnabled":true}""")
        val detector = mockk<ILogAnrDetector>(relaxed = true)

        newManager(anrDetector = detector).initializeFromCachedConfig()

        Thread.getDefaultUncaughtExceptionHandler().shouldBeInstanceOf<AndroidLogCrashHandler>()
        verify { detector.start() }
    }

    // Reading the absent flag as off would take observability away from every upgrading install.

    test("initializeFromCachedConfig starts the pipeline for a cache written before isEnabled existed") {
        writeCachedLoggingParams("""{"logLevel":"ERROR"}""")
        val detector = mockk<ILogAnrDetector>(relaxed = true)

        newManager(anrDetector = detector).initializeFromCachedConfig()

        Thread.getDefaultUncaughtExceptionHandler().shouldBeInstanceOf<AndroidLogCrashHandler>()
        verify { detector.start() }
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

    // The watchdog and sink are invisible to the global handler, so drive them via the factories.

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
        // Emission hops to a background scope, so wait on a signal rather than a fixed sleep. The
        // negative case has no event, so it settles for a bounded wait once the pipeline is warm.
        val emitted = CompletableDeferred<Unit>()
        val telemetry = mockk<ILogTelemetryRemote>(relaxed = true)
        coEvery { telemetry.emit(any()) } answers { emitted.complete(Unit); Unit }
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
        Logging.error("routed while enabled")
        runBlocking { withTimeout(SINK_TIMEOUT_MS) { emitted.await() } }
        coVerify { telemetry.emit(any()) }

        manager.onModelReplaced(configWith(isEnabled = false, logLevel = null), ModelChangeTags.HYDRATE)
        verify { telemetry.shutdown() }

        clearMocks(telemetry, answers = false)
        Logging.error("dropped after disable")
        runBlocking { delay(SINK_QUIET_MS) }
        coVerify(exactly = 0) { telemetry.emit(any()) }
    }
})

/** Generous upper bound on a signal we expect; only a hang burns the full budget. */
private const val SINK_TIMEOUT_MS = 5_000L

/** Settle window for asserting the detached sink stays silent. */
private const val SINK_QUIET_MS = 200L

private fun configWith(isEnabled: Boolean, logLevel: LogLevel?): ConfigModel {
    val config = ConfigModel()
    config.remoteLoggingParams.isEnabled = isEnabled
    logLevel?.let { config.remoteLoggingParams.logLevel = it }
    return config
}
