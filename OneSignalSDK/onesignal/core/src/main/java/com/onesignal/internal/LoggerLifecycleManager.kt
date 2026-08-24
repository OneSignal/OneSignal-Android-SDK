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
 * Owns the lifecycle of the SDK's multiplatform observability pipeline (remote logging,
 * crash capture, ANR detection) and reacts to remote config changes via the shared
 * [ObservabilityConfig]/[ObservabilityConfigEvaluator].
 *
 * Production callers supply only [context] and [featureManagerProvider]; every other
 * parameter defaults to the real implementation, so runtime wiring is unchanged. Tests
 * override them to inject mocks or throwing stubs.
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

    /** Must be called while holding [lock]. */
    private fun applyAction(action: ObservabilityConfigAction, newConfig: ObservabilityConfig) {
        when (action) {
            is ObservabilityConfigAction.Enable -> enableFeatures(newConfig.logLevel ?: LogLevel.ERROR)
            is ObservabilityConfigAction.Disable -> disableFeatures()
            is ObservabilityConfigAction.UpdateLogLevel -> updateLogLevel(action.newLevel)
            is ObservabilityConfigAction.NoChange -> Logging.debug("OneSignal: logger config unchanged")
        }
        currentConfig = newConfig
    }

    @Suppress("TooGenericExceptionCaught")
    private fun enableFeatures(logLevel: LogLevel) {
        Logging.info("OneSignal: Enabling logger module features at level $logLevel")
        try {
            startCrashHandler()
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Failed to start logger crash handler: ${t.message}", t)
        }
        try {
            startAnrDetector()
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Failed to start logger ANR detector: ${t.message}", t)
        }
        try {
            startLogging(logLevel)
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Failed to start logger logging: ${t.message}", t)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun disableFeatures() {
        Logging.info("OneSignal: Disabling logger module features")
        // Each reference is cleared before the teardown call, not after. A collaborator that
        // throws on the way down would otherwise leave its field set, and the start guards
        // below would then treat the dead component as already running — permanently
        // disabling it for the rest of the process.
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
            Logging.setLoggerTelemetry(null) { false }
            telemetry?.shutdown()
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Error disabling logger logging: ${t.message}", t)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun updateLogLevel(newLevel: LogLevel) {
        Logging.info("OneSignal: Updating logger module log level to $newLevel")
        try {
            startLogging(newLevel)
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Failed to update logger log level: ${t.message}", t)
        }
    }

    private fun startCrashHandler() {
        if (crashHandler != null) return
        val handler = crashHandlerFactory(platformProvider, fileStore, logger)
        handler.initialize()
        crashHandler = handler
        Logging.info("OneSignal: logger crash handler initialized — logs at: ${platformProvider.crashStoragePath}")
    }

    private fun startAnrDetector() {
        if (anrDetector != null) return
        val detector = anrDetectorFactory(platformProvider, fileStore, logger)
        detector.start()
        anrDetector = detector
        Logging.info("OneSignal: logger ANR detector started")
    }

    private fun startLogging(logLevel: LogLevel) {
        remoteTelemetry?.shutdown()
        val telemetry = remoteTelemetryFactory(platformProvider, httpSender)
        remoteTelemetry = telemetry
        val shouldSend: (LogLevel) -> Boolean = { level ->
            logLevel != LogLevel.NONE && level <= logLevel
        }
        Logging.setLoggerTelemetry(telemetry, shouldSend)
        Logging.info("OneSignal: logger module logging active at level $logLevel")
    }
}
