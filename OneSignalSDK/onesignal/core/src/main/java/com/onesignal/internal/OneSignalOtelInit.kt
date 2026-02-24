package com.onesignal.internal

import android.content.Context
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.crash.OneSignalCrashHandlerFactory
import com.onesignal.debug.internal.crash.OtelSdkSupport
import com.onesignal.debug.internal.crash.createAnrDetector
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.debug.internal.logging.otel.android.AndroidOtelLogger
import com.onesignal.debug.internal.logging.otel.android.OtelPlatformProvider
import com.onesignal.debug.internal.logging.otel.android.createAndroidOtelPlatformProvider
import com.onesignal.otel.IOtelCrashHandler
import com.onesignal.otel.IOtelOpenTelemetryRemote
import com.onesignal.otel.OtelFactory
import com.onesignal.otel.crash.IOtelAnrDetector

/**
 * Initializes all Otel-based observability features: crash reporting, ANR detection,
 * and remote log shipping.
 *
 * Creates and reuses a single OtelPlatformProvider instance across all features.
 */
internal class OneSignalOtelInit(
    private val context: Context,
) {
    // Platform provider - created once and reused for both crash handler and logging
    private val platformProvider: OtelPlatformProvider by lazy {
        createAndroidOtelPlatformProvider(context)
    }

    @Suppress("TooGenericExceptionCaught")
    fun initializeCrashHandler() {
        if (!OtelSdkSupport.isSupported) {
            Logging.info("OneSignal: Device SDK < ${OtelSdkSupport.MIN_SDK_VERSION}, Otel not supported — skipping crash handler and ANR detector")
            return
        }
        if (!platformProvider.isRemoteLoggingEnabled) {
            Logging.info("OneSignal: Remote logging disabled (not yet enabled via config), skipping crash handler and ANR detector")
            return
        }

        try {
            Logging.info("OneSignal: Initializing crash handler early...")

            // Use factory which handles SDK version checks (no-op for SDK < 26)
            val logger = AndroidOtelLogger()
            val crashHandler: IOtelCrashHandler = OneSignalCrashHandlerFactory.createCrashHandler(context, logger)

            Logging.info("OneSignal: Crash handler created, initializing...")
            crashHandler.initialize()

            Logging.info("OneSignal: ✅ Crash handler initialized successfully and ready to capture crashes")
            Logging.info("OneSignal: 📁 Crash logs will be stored at: ${platformProvider.crashStoragePath}")

            // Initialize ANR detector (standalone, monitors main thread for ANRs)
            try {
                Logging.info("OneSignal: Initializing ANR detector...")
                val anrDetector: IOtelAnrDetector = createAnrDetector(
                    platformProvider,
                    logger,
                    anrThresholdMs = com.onesignal.debug.internal.crash.AnrConstants.DEFAULT_ANR_THRESHOLD_MS,
                    checkIntervalMs = com.onesignal.debug.internal.crash.AnrConstants.DEFAULT_CHECK_INTERVAL_MS
                )
                anrDetector.start()
                Logging.info("OneSignal: ✅ ANR detector initialized and started")
            } catch (e: Exception) {
                Logging.error("OneSignal: Failed to initialize ANR detector: ${e.message}", e)
            }
        } catch (e: Exception) {
            Logging.error("OneSignal: Failed to initialize crash handler: ${e.message}", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun initializeOtelLogging() {
        if (!OtelSdkSupport.isSupported) {
            Logging.info("OneSignal: Device SDK < ${OtelSdkSupport.MIN_SDK_VERSION}, Otel not supported — skipping Otel logging")
            return
        }
        if (!platformProvider.isRemoteLoggingEnabled) {
            Logging.info("OneSignal: Remote logging disabled, skipping Otel logging integration")
            return
        }
        // Initialize Otel logging asynchronously to avoid blocking initialization
        // Remote logging is not critical for crashes, so it's safe to do this in the background
        // Uses OtelIdResolver internally which reads directly from SharedPreferences
        // No service dependencies required - fully decoupled from service architecture
        suspendifyOnIO {
            try {
                // Reuses the same platform provider instance created for crash handler
                // Get the remote log level as string (defaults to "ERROR" if null, "NONE" if explicitly set)
                val remoteLogLevelStr = platformProvider.remoteLogLevel

                // Check if remote logging is enabled (not NONE)
                if (remoteLogLevelStr != null && remoteLogLevelStr != "NONE") {
                    // Store in local variable for smart cast
                    val logLevelStr = remoteLogLevelStr
                    Logging.info("OneSignal: Remote logging enabled at level $logLevelStr, initializing Otel logging integration...")
                    val remoteTelemetry: IOtelOpenTelemetryRemote = OtelFactory.createRemoteTelemetry(platformProvider)

                    // Parse the log level string to LogLevel enum for comparison
                    @Suppress("TooGenericExceptionCaught", "SwallowedException")
                    val remoteLogLevel: LogLevel = try {
                        LogLevel.valueOf(logLevelStr)
                    } catch (e: Exception) {
                        LogLevel.ERROR // Default to ERROR on parse error
                    }

                    // Create a function that checks if a log level should be sent remotely
                    // - If remoteLogLevel is null: default to ERROR (send ERROR and above)
                    // - If remoteLogLevel is NONE: don't send anything (shouldn't reach here, but handle it)
                    // - Otherwise: send logs at that level and above
                    val shouldSendLogLevel: (LogLevel) -> Boolean = { level ->
                        when {
                            remoteLogLevel == LogLevel.NONE -> false // Don't send anything
                            else -> level >= remoteLogLevel // Send at configured level and above
                        }
                    }

                    // Inject Otel telemetry into Logging class
                    Logging.setOtelTelemetry(remoteTelemetry, shouldSendLogLevel)
                    Logging.info("OneSignal: ✅ Otel logging integration initialized - logs at level $logLevelStr and above will be sent to remote server")
                } else {
                    Logging.debug("OneSignal: Remote logging disabled (level: $remoteLogLevelStr), skipping Otel logging integration")
                }
            } catch (e: Exception) {
                // If Otel logging initialization fails, log it but don't crash
                Logging.warn("OneSignal: Failed to initialize Otel logging: ${e.message}", e)
            }
        }
    }
}
