package com.onesignal.internal

import android.content.Context
import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.crash.AnrConstants
import com.onesignal.debug.internal.crash.ObservabilitySdkSupport
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.debug.internal.logging.logger.android.AndroidLogAnrDetector
import com.onesignal.debug.internal.logging.logger.android.AndroidLogCrashHandler
import com.onesignal.debug.internal.logging.logger.android.AndroidLogger
import com.onesignal.debug.internal.logging.logger.android.FileLogStore
import com.onesignal.debug.internal.logging.logger.android.OneSignalLogHttpSender
import com.onesignal.debug.internal.logging.logger.android.createAndroidLoggerPlatformProvider
import com.onesignal.logger.ILogAnrDetector
import com.onesignal.logger.ILogCrashHandler
import com.onesignal.logger.ILogCrashReporter
import com.onesignal.logger.ILogFileStore
import com.onesignal.logger.ILogHttpSender
import com.onesignal.logger.ILogTelemetryRemote
import com.onesignal.logger.ILogger
import com.onesignal.logger.ILoggerPlatformProvider
import com.onesignal.logger.LoggerFactory

/** Shared by the crash-handler and ANR-detector defaults, which each report through their own reporter. */
private fun createReporter(
    platformProvider: ILoggerPlatformProvider,
    fileStore: ILogFileStore,
    logger: ILogger,
): ILogCrashReporter =
    LoggerFactory.createCrashReporter(
        LoggerFactory.createCrashLocalTelemetry(platformProvider, fileStore),
        logger,
    )

/**
 * Owns the observability pipeline (remote logging, crash capture, ANR detection) and reacts to
 * config changes. Beyond [context] and [featureManagerProvider], the parameters are test seams.
 */
@Suppress("TooManyFunctions", "LongParameterList")
internal class LoggerLifecycleManager(
    private val context: Context,
    private val featureManagerProvider: () -> IFeatureManager,
    private val platformProviderFactory: (Context, () -> IFeatureManager) -> ILoggerPlatformProvider =
        { ctx, fm -> createAndroidLoggerPlatformProvider(ctx, fm) },
    private val logger: ILogger = AndroidLogger(),
    private val fileStoreFactory: (String) -> ILogFileStore = { path -> FileLogStore(path) },
    private val crashHandlerFactory: (ILoggerPlatformProvider, ILogFileStore, ILogger) -> ILogCrashHandler =
        { pp, store, log -> AndroidLogCrashHandler(createReporter(pp, store, log), log) },
    private val anrDetectorFactory: (ILoggerPlatformProvider, ILogFileStore, ILogger) -> ILogAnrDetector =
        { pp, store, log ->
            AndroidLogAnrDetector(
                createReporter(pp, store, log),
                log,
                AnrConstants.DEFAULT_ANR_THRESHOLD_MS,
                AnrConstants.DEFAULT_CHECK_INTERVAL_MS,
                AnrConstants.DEFAULT_BACKGROUND_BLOCK_THRESHOLD_MS,
                // Only "background" downgrades a block to a non-fatal warning; "unknown" is
                // treated as foreground so a genuine ANR is never silently dropped.
                isAppInForeground = { pp.appState != "background" },
            )
        },
    private val remoteTelemetryFactory: (ILoggerPlatformProvider, ILogHttpSender) -> ILogTelemetryRemote =
        { pp, sender -> LoggerFactory.createRemoteTelemetry(pp, sender) },
) : ISingletonModelStoreChangeHandler<ConfigModel>, IObservabilityLifecycleManager {
    private val lock = Any()

    private val platformProvider: ILoggerPlatformProvider by lazy {
        platformProviderFactory(context, featureManagerProvider)
    }

    private val httpSender = OneSignalLogHttpSender(logger) { platformProvider.isExporterLoggingEnabled }

    private val fileStore: ILogFileStore by lazy { fileStoreFactory(platformProvider.crashStoragePath) }

    private var crashHandler: ILogCrashHandler? = null
    private var anrDetector: ILogAnrDetector? = null
    private var remoteTelemetry: ILogTelemetryRemote? = null
    private var currentConfig: ObservabilityConfig? = null

    /** Level the live sink is actually filtering at, which is not always [currentConfig]'s level. */
    private var activeLogLevel: LogLevel? = null

    @Suppress("TooGenericExceptionCaught")
    override fun initializeFromCachedConfig() {
        if (!ObservabilitySdkSupport.isSupported) {
            Logging.info("OneSignal: Device SDK < ${ObservabilitySdkSupport.MIN_SDK_VERSION}, logger module not supported — skipping")
            return
        }
        try {
            val cachedConfig = readCurrentCachedConfig()
            synchronized(lock) {
                val action = ObservabilityConfigEvaluator.evaluate(old = currentConfig, new = cachedConfig)
                applyAction(action, cachedConfig)
            }
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Failed to initialize logger module from cached config: ${t.message}", t)
        }
    }

    override fun subscribeToConfigStore(configModelStore: ConfigModelStore) {
        configModelStore.subscribe(this)
    }

    @Suppress("TooGenericExceptionCaught")
    override fun onModelReplaced(model: ConfigModel, tag: String) {
        if (tag != ModelChangeTags.HYDRATE) return
        if (!ObservabilitySdkSupport.isSupported) return
        try {
            val newConfig =
                ObservabilityConfig(
                    isEnabled = model.remoteLoggingParams.isEnabled,
                    logLevel = model.remoteLoggingParams.logLevel,
                )
            synchronized(lock) {
                val action = ObservabilityConfigEvaluator.evaluate(old = currentConfig, new = newConfig)
                applyAction(action, newConfig)
            }
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Failed to refresh logger module from remote config: ${t.message}", t)
        }
    }

    override fun onModelUpdated(args: ModelChangedArgs, tag: String) {
        // Only full model replacements (HYDRATE) matter here.
    }

    private fun readCurrentCachedConfig(): ObservabilityConfig {
        val enabled = platformProvider.isRemoteLoggingEnabled
        val level = LogLevel.fromString(platformProvider.remoteLogLevel)
        return ObservabilityConfig(isEnabled = enabled, logLevel = level)
    }

    /**
     * Call while holding [lock]. [currentConfig] advances only once the requested state is actually
     * in place, so a component that failed to start is retried by the next HYDRATE.
     */
    private fun applyAction(action: ObservabilityConfigAction, newConfig: ObservabilityConfig) {
        // Honor "off" from actual liveness rather than the diff: a partial-failure Enable leaves
        // components live under a config never committed, so the kill switch would be ignored.
        if (!newConfig.isEnabled && isAnyFeatureLive()) {
            disableFeatures()
            currentConfig = newConfig
            return
        }
        val applied =
            when (action) {
                is ObservabilityConfigAction.Enable -> enableFeatures(newConfig.logLevel ?: LogLevel.ERROR)
                is ObservabilityConfigAction.UpdateLogLevel -> updateLogLevel(action.newLevel)
                is ObservabilityConfigAction.Disable -> {
                    disableFeatures()
                    true
                }
                is ObservabilityConfigAction.NoChange -> reconcileUnchangedConfig(newConfig)
            }
        if (applied) currentConfig = newConfig
    }

    /**
     * `NoChange` only means the desired config is unchanged, and a stable payload keeps evaluating
     * to it, so decide off actual liveness — there may be no later evaluation at all.
     */
    private fun reconcileUnchangedConfig(newConfig: ObservabilityConfig): Boolean {
        val logLevel = newConfig.logLevel ?: LogLevel.ERROR
        if (!newConfig.isEnabled || isEveryFeatureLive(logLevel)) {
            Logging.debug("OneSignal: logger config unchanged")
            return true
        }
        return enableFeatures(logLevel)
    }

    private fun isAnyFeatureLive(): Boolean = crashHandler != null || anrDetector != null || remoteTelemetry != null

    private fun isEveryFeatureLive(logLevel: LogLevel): Boolean =
        crashHandler != null && anrDetector != null && remoteTelemetry != null && activeLogLevel == logLevel

    /**
     * Starts whatever is not already running; one component failing must not stop the others.
     * @return true when every feature is up, so the caller knows whether to commit the config
     */
    @Suppress("TooGenericExceptionCaught")
    private fun enableFeatures(logLevel: LogLevel): Boolean {
        Logging.info("OneSignal: Enabling logger module features at level $logLevel")
        var allStarted = true
        try {
            startCrashHandler()
        } catch (t: Throwable) {
            allStarted = false
            Logging.warn("OneSignal: Failed to start logger crash handler: ${t.message}", t)
        }
        try {
            startAnrDetector()
        } catch (t: Throwable) {
            allStarted = false
            Logging.warn("OneSignal: Failed to start logger ANR detector: ${t.message}", t)
        }
        try {
            // Guarded like the other two so a retry does not tear down a healthy sink, but a retry
            // can carry a newer level, so compare against the level actually in force.
            if (remoteTelemetry == null || activeLogLevel != logLevel) startLogging(logLevel)
        } catch (t: Throwable) {
            allStarted = false
            Logging.warn("OneSignal: Failed to start logger logging: ${t.message}", t)
        }
        if (!allStarted) {
            Logging.warn("OneSignal: Some logger features did not start; will retry on the next config refresh")
        }
        return allStarted
    }

    @Suppress("TooGenericExceptionCaught")
    private fun disableFeatures() {
        Logging.info("OneSignal: Disabling logger module features")
        // Clear each reference before the teardown call: a collaborator that throws on the way
        // down would otherwise leave its field set, and the start guards would treat it as live.
        try {
            val detector = anrDetector
            anrDetector = null
            detector?.stop()
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Error stopping logger ANR detector: ${t.message}", t)
        }
        try {
            val handler = crashHandler
            crashHandler = null
            handler?.unregister()
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Error unregistering logger crash handler: ${t.message}", t)
        }
        try {
            val telemetry = remoteTelemetry
            remoteTelemetry = null
            activeLogLevel = null
            Logging.setLoggerTelemetry(null) { false }
            telemetry?.shutdown()
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Error disabling logger logging: ${t.message}", t)
        }
    }

    /** @return true when the new level is live, so the caller knows whether to commit the config */
    @Suppress("TooGenericExceptionCaught")
    private fun updateLogLevel(newLevel: LogLevel): Boolean {
        Logging.info("OneSignal: Updating logger module log level to $newLevel")
        return try {
            startLogging(newLevel)
            true
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Failed to update logger log level: ${t.message}", t)
            false
        }
    }

    /**
     * [ILogCrashHandler.initialize] can chain onto the process-global handler before it throws, so
     * a failed start must unregister — abandoning it lets the retry chain a second one.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun startCrashHandler() {
        if (crashHandler != null) return
        val handler = crashHandlerFactory(platformProvider, fileStore, logger)
        try {
            handler.initialize()
        } catch (t: Throwable) {
            try {
                handler.unregister()
            } catch (inner: Throwable) {
                Logging.warn("OneSignal: Error unwinding a partially initialized logger crash handler: ${inner.message}", inner)
            }
            throw t
        }
        crashHandler = handler
        Logging.info("OneSignal: logger crash handler initialized — logs at: ${platformProvider.crashStoragePath}")
    }

    /** Same partial-start unwind as [startCrashHandler]: a throwing start() may already have spawned its watchdog. */
    @Suppress("TooGenericExceptionCaught")
    private fun startAnrDetector() {
        if (anrDetector != null) return
        val detector = anrDetectorFactory(platformProvider, fileStore, logger)
        try {
            detector.start()
        } catch (t: Throwable) {
            try {
                detector.stop()
            } catch (inner: Throwable) {
                Logging.warn("OneSignal: Error unwinding a partially started logger ANR detector: ${inner.message}", inner)
            }
            throw t
        }
        anrDetector = detector
        Logging.info("OneSignal: logger ANR detector started")
    }

    @Suppress("TooGenericExceptionCaught")
    private fun startLogging(logLevel: LogLevel) {
        // Build the replacement before parting with the incumbent: a throwing factory must cost
        // nothing, so a failed level change leaves the working sink serving at its old level. The
        // field and Logging's global then move together, before the old sink is shut down.
        val telemetry = remoteTelemetryFactory(platformProvider, httpSender)
        val shouldSend: (LogLevel) -> Boolean = { level ->
            logLevel != LogLevel.NONE && level <= logLevel
        }
        val previous = remoteTelemetry
        remoteTelemetry = telemetry
        activeLogLevel = logLevel
        Logging.setLoggerTelemetry(telemetry, shouldSend)
        try {
            previous?.shutdown()
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Error shutting down previous logger telemetry: ${t.message}", t)
        }
        Logging.info("OneSignal: logger module logging active at level $logLevel")
    }
}
