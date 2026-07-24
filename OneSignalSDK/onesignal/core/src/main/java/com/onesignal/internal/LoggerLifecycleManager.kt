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
import com.onesignal.debug.internal.crash.OtelSdkSupport
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.debug.internal.logging.logger.android.AndroidLogAnrDetector
import com.onesignal.debug.internal.logging.logger.android.AndroidLogCrashHandler
import com.onesignal.debug.internal.logging.logger.android.AndroidLogger
import com.onesignal.debug.internal.logging.logger.android.FileLogStore
import com.onesignal.debug.internal.logging.logger.android.OneSignalLogHttpSender
import com.onesignal.debug.internal.logging.logger.android.createAndroidLoggerPlatformProvider
import com.onesignal.logger.ILogAnrDetector
import com.onesignal.logger.ILogCrashHandler
import com.onesignal.logger.ILogTelemetryRemote
import com.onesignal.logger.ILoggerPlatformProvider
import com.onesignal.logger.LoggerFactory

/**
 * The `logger` module counterpart to [OtelLifecycleManager]. Owns the lifecycle of the
 * multiplatform, OpenTelemetry-free observability pipeline and reacts to remote config
 * changes the same way (using the shared [OtelConfig]/[OtelConfigEvaluator]).
 *
 * Only active when [com.onesignal.debug.internal.logging.logger.LoggerModuleSwitch.USE_LOGGER_MODULE]
 * is true; otherwise [OtelLifecycleManager] is used instead.
 */
@Suppress("TooManyFunctions")
internal class LoggerLifecycleManager(
    private val context: Context,
    private val featureManagerProvider: () -> IFeatureManager,
) : ISingletonModelStoreChangeHandler<ConfigModel>, IObservabilityLifecycleManager {
    private val lock = Any()

    private val platformProvider: ILoggerPlatformProvider by lazy {
        createAndroidLoggerPlatformProvider(context, featureManagerProvider)
    }

    private val logger = AndroidLogger()
    private val httpSender = OneSignalLogHttpSender(logger) { platformProvider.isExporterLoggingEnabled }

    private val fileStore: FileLogStore by lazy { FileLogStore(platformProvider.crashStoragePath) }

    private var crashHandler: ILogCrashHandler? = null
    private var anrDetector: ILogAnrDetector? = null
    private var remoteTelemetry: ILogTelemetryRemote? = null
    private var currentConfig: OtelConfig? = null

    @Suppress("TooGenericExceptionCaught")
    override fun initializeFromCachedConfig() {
        if (!OtelSdkSupport.isSupported) {
            Logging.info("OneSignal: Device SDK < ${OtelSdkSupport.MIN_SDK_VERSION}, logger module not supported — skipping")
            return
        }
        try {
            val cachedConfig = readCurrentCachedConfig()
            synchronized(lock) {
                val action = OtelConfigEvaluator.evaluate(old = currentConfig, new = cachedConfig)
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
        if (!OtelSdkSupport.isSupported) return
        try {
            val newConfig =
                OtelConfig(
                    isEnabled = model.remoteLoggingParams.isEnabled,
                    logLevel = model.remoteLoggingParams.logLevel,
                )
            synchronized(lock) {
                val action = OtelConfigEvaluator.evaluate(old = currentConfig, new = newConfig)
                applyAction(action, newConfig)
            }
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Failed to refresh logger module from remote config: ${t.message}", t)
        }
    }

    override fun onModelUpdated(args: ModelChangedArgs, tag: String) {
        // Only full model replacements (HYDRATE) matter here.
    }

    private fun readCurrentCachedConfig(): OtelConfig {
        val enabled = platformProvider.isRemoteLoggingEnabled
        val level = LogLevel.fromString(platformProvider.remoteLogLevel)
        return OtelConfig(isEnabled = enabled, logLevel = level)
    }

    /** Must be called while holding [lock]. */
    private fun applyAction(action: OtelConfigAction, newConfig: OtelConfig) {
        when (action) {
            is OtelConfigAction.Enable -> enableFeatures(newConfig.logLevel ?: LogLevel.ERROR)
            is OtelConfigAction.Disable -> disableFeatures()
            is OtelConfigAction.UpdateLogLevel -> updateLogLevel(action.newLevel)
            is OtelConfigAction.NoChange -> Logging.debug("OneSignal: logger config unchanged")
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
        try {
            anrDetector?.stop()
            anrDetector = null
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Error stopping logger ANR detector: ${t.message}", t)
        }
        try {
            crashHandler?.unregister()
            crashHandler = null
        } catch (t: Throwable) {
            Logging.warn("OneSignal: Error unregistering logger crash handler: ${t.message}", t)
        }
        try {
            Logging.setLoggerTelemetry(null) { false }
            remoteTelemetry?.shutdown()
            remoteTelemetry = null
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
        val crashTelemetry = LoggerFactory.createCrashLocalTelemetry(platformProvider, fileStore)
        val reporter = LoggerFactory.createCrashReporter(crashTelemetry, logger)
        val handler = AndroidLogCrashHandler(reporter, logger)
        handler.initialize()
        crashHandler = handler
        Logging.info("OneSignal: logger crash handler initialized — logs at: ${platformProvider.crashStoragePath}")
    }

    private fun startAnrDetector() {
        if (anrDetector != null) return
        val crashTelemetry = LoggerFactory.createCrashLocalTelemetry(platformProvider, fileStore)
        val reporter = LoggerFactory.createCrashReporter(crashTelemetry, logger)
        val detector =
            AndroidLogAnrDetector(
                reporter,
                logger,
                AnrConstants.DEFAULT_ANR_THRESHOLD_MS,
                AnrConstants.DEFAULT_CHECK_INTERVAL_MS,
                AnrConstants.DEFAULT_BACKGROUND_BLOCK_THRESHOLD_MS,
                // Only "background" downgrades a block to a non-fatal warning; "unknown" is
                // treated as foreground so a genuine ANR is never silently dropped.
                isAppInForeground = { platformProvider.appState != "background" },
            )
        detector.start()
        anrDetector = detector
        Logging.info("OneSignal: logger ANR detector started")
    }

    private fun startLogging(logLevel: LogLevel) {
        remoteTelemetry?.shutdown()
        val telemetry = LoggerFactory.createRemoteTelemetry(platformProvider, httpSender)
        remoteTelemetry = telemetry
        val shouldSend: (LogLevel) -> Boolean = { level ->
            logLevel != LogLevel.NONE && level <= logLevel
        }
        Logging.setLoggerTelemetry(telemetry, shouldSend)
        Logging.info("OneSignal: logger module logging active at level $logLevel")
    }
}
