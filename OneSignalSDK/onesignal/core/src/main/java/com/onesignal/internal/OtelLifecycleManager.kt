package com.onesignal.internal

import android.content.Context
import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.crash.AnrConstants
import com.onesignal.debug.internal.crash.OneSignalCrashHandlerFactory
import com.onesignal.debug.internal.crash.OtelSdkSupport
import com.onesignal.debug.internal.crash.createAnrDetector
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.debug.internal.logging.otel.android.AndroidOtelLogger
import com.onesignal.debug.internal.logging.otel.android.OtelPlatformProvider
import com.onesignal.debug.internal.logging.otel.android.createAndroidOtelPlatformProvider
import com.onesignal.otel.IOtelCrashHandler
import com.onesignal.otel.IOtelLogger
import com.onesignal.otel.IOtelOpenTelemetryRemote
import com.onesignal.otel.IOtelPlatformProvider
import com.onesignal.otel.OtelFactory
import com.onesignal.otel.crash.IOtelAnrDetector

/**
 * Owns the lifecycle of all Otel-based observability features and reacts
 * to remote config changes so features can be enabled, disabled, or
 * have their log level updated mid-session.
 *
 * Subscribes to [ConfigModelStore] via [ISingletonModelStoreChangeHandler]
 * so that when fresh remote config arrives (HYDRATE), Otel features are
 * automatically started, stopped, or updated.
 *
 * Thread safety: methods are synchronized on [lock] so that concurrent
 * calls from initEssentials (main) and the config store callback (IO) are safe.
 *
 * All factory parameters default to the real implementations, so production
 * callers can use `OtelLifecycleManager(context)`. Tests can override any
 * factory to inject mocks or throwing stubs.
 */
@Suppress("TooManyFunctions")
internal class OtelLifecycleManager(
    private val context: Context,
    private val crashHandlerFactory: (Context, IOtelLogger) -> IOtelCrashHandler =
        { ctx, log -> OneSignalCrashHandlerFactory.createCrashHandler(ctx, log) },
    private val anrDetectorFactory: (IOtelPlatformProvider, IOtelLogger, Long, Long) -> IOtelAnrDetector =
        { pp, log, threshold, interval -> createAnrDetector(pp, log, threshold, interval) },
    private val remoteTelemetryFactory: (IOtelPlatformProvider) -> IOtelOpenTelemetryRemote =
        { pp -> OtelFactory.createRemoteTelemetry(pp) },
    private val platformProviderFactory: (Context) -> OtelPlatformProvider =
        { ctx -> createAndroidOtelPlatformProvider(ctx) },
    private val loggerFactory: () -> IOtelLogger = { AndroidOtelLogger() },
) : ISingletonModelStoreChangeHandler<ConfigModel> {
    private val lock = Any()

    private val platformProvider: OtelPlatformProvider by lazy {
        platformProviderFactory(context)
    }

    private val logger: IOtelLogger by lazy { loggerFactory() }

    private var crashHandler: IOtelCrashHandler? = null
    private var anrDetector: IOtelAnrDetector? = null
    private var remoteTelemetry: IOtelOpenTelemetryRemote? = null
    private var currentConfig: OtelConfig? = null

    /**
     * Called once from [OneSignalImp.initEssentials] at cold start.
     * Reads the cached config from SharedPreferences and boots
     * whichever features are already enabled.
     */
    @Suppress("TooGenericExceptionCaught")
    fun initializeFromCachedConfig() {
        if (!OtelSdkSupport.isSupported) {
            Logging.info("OneSignal: Device SDK < ${OtelSdkSupport.MIN_SDK_VERSION}, Otel not supported — skipping all Otel features")
            return
        }

        try {
            val cachedConfig = readCurrentCachedConfig()
            synchronized(lock) {
                val action = OtelConfigEvaluator.evaluate(old = currentConfig, new = cachedConfig)
                applyAction(action, cachedConfig)
            }
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Failed to initialize Otel from cached config: ${t.message}", t)
        }
    }

    /**
     * Subscribes this manager to config store change events.
     * Call after the IoC container is bootstrapped (i.e. after [bootstrapServices]).
     */
    fun subscribeToConfigStore(configModelStore: ConfigModelStore) {
        configModelStore.subscribe(this)
    }

    // ------------------------------------------------------------------
    // ISingletonModelStoreChangeHandler<ConfigModel>
    // ------------------------------------------------------------------

    @Suppress("TooGenericExceptionCaught")
    override fun onModelReplaced(model: ConfigModel, tag: String) {
        if (tag != ModelChangeTags.HYDRATE) return
        if (!OtelSdkSupport.isSupported) return

        try {
            val logLevel = model.remoteLoggingParams.logLevel
            val isEnabled = model.remoteLoggingParams.isEnabled
            val newConfig = OtelConfig(isEnabled = isEnabled, logLevel = logLevel)
            synchronized(lock) {
                val action = OtelConfigEvaluator.evaluate(old = currentConfig, new = newConfig)
                applyAction(action, newConfig)
            }
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Failed to refresh Otel from remote config: ${t.message}", t)
        }
    }

    override fun onModelUpdated(args: ModelChangedArgs, tag: String) {
        // We only care about full model replacements (HYDRATE), not individual property changes.
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private fun readCurrentCachedConfig(): OtelConfig {
        val enabled = platformProvider.isRemoteLoggingEnabled
        val level = LogLevel.fromString(platformProvider.remoteLogLevel)
        return OtelConfig(isEnabled = enabled, logLevel = level)
    }

    /** Must be called while holding [lock]. */
    @Suppress("TooGenericExceptionCaught")
    private fun applyAction(action: OtelConfigAction, newConfig: OtelConfig) {
        when (action) {
            is OtelConfigAction.Enable -> enableFeatures(newConfig.logLevel ?: LogLevel.ERROR)
            is OtelConfigAction.Disable -> disableFeatures()
            is OtelConfigAction.UpdateLogLevel -> updateLogLevel(action.newLevel)
            is OtelConfigAction.NoChange -> {
                Logging.debug("OneSignal: Otel config unchanged, no action needed")
            }
        }
        currentConfig = newConfig
    }

    @Suppress("TooGenericExceptionCaught")
    private fun enableFeatures(logLevel: LogLevel) {
        Logging.info("OneSignal: Enabling Otel features at level $logLevel")

        try {
            startCrashHandler()
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Failed to start crash handler: ${t.message}", t)
        }

        try {
            startAnrDetector()
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Failed to start ANR detector: ${t.message}", t)
        }

        try {
            startOtelLogging(logLevel)
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Failed to start Otel logging: ${t.message}", t)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun disableFeatures() {
        Logging.info("OneSignal: Disabling Otel features")

        try {
            anrDetector?.stop()
            anrDetector = null
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Error stopping ANR detector: ${t.message}", t)
        }

        try {
            crashHandler?.unregister()
            crashHandler = null
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Error unregistering crash handler: ${t.message}", t)
        }

        try {
            Logging.setOtelTelemetry(null, { false })
            remoteTelemetry?.shutdown()
            remoteTelemetry = null
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Error disabling Otel logging: ${t.message}", t)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun updateLogLevel(newLevel: LogLevel) {
        Logging.info("OneSignal: Updating Otel log level to $newLevel")
        try {
            startOtelLogging(newLevel)
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Failed to update Otel log level: ${t.message}", t)
        }
    }

    private fun startCrashHandler() {
        if (crashHandler != null) return
        val handler = crashHandlerFactory(context, logger)
        handler.initialize()
        crashHandler = handler
        Logging.info("OneSignal: Crash handler initialized — logs at: ${platformProvider.crashStoragePath}")
    }

    private fun startAnrDetector() {
        if (anrDetector != null) return
        val detector = anrDetectorFactory(
            platformProvider,
            logger,
            AnrConstants.DEFAULT_ANR_THRESHOLD_MS,
            AnrConstants.DEFAULT_CHECK_INTERVAL_MS,
        )
        detector.start()
        anrDetector = detector
        Logging.info("OneSignal: ANR detector started")
    }

    @Suppress("TooGenericExceptionCaught")
    private fun startOtelLogging(logLevel: LogLevel) {
        remoteTelemetry?.shutdown()
        val telemetry = remoteTelemetryFactory(platformProvider)
        remoteTelemetry = telemetry
        val shouldSend: (LogLevel) -> Boolean = { level ->
            logLevel != LogLevel.NONE && level <= logLevel
        }
        Logging.setOtelTelemetry(telemetry, shouldSend)
        Logging.info("OneSignal: Otel logging active at level $logLevel")
    }
}
