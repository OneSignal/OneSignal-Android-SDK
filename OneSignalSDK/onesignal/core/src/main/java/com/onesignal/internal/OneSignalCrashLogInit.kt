package com.onesignal.internal

import android.content.Context
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.crash.createAnrDetector
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.debug.internal.logging.otel.android.AndroidOtelLogger
import com.onesignal.debug.internal.logging.otel.android.OtelPlatformProvider
import com.onesignal.debug.internal.logging.otel.android.createAndroidOtelPlatformProvider
import com.onesignal.otel.IOtelCrashHandler
import com.onesignal.otel.OtelFactory
import com.onesignal.otel.crash.IOtelAnrDetector

/**
 * Helper class for OneSignal initialization tasks.
 * Extracted from OneSignalImp to reduce class size and improve maintainability.
 *
 * Creates and reuses a single OtelPlatformProvider instance for both crash handler and logging.
 */
internal class OneSignalCrashLogInit(
    private val context: Context,
) {
    // Platform provider - created once and reused for both crash handler and logging
    private val platformProvider: OtelPlatformProvider by lazy {
        createAndroidOtelPlatformProvider(context)
    }

    @Suppress("TooGenericExceptionCaught")
    fun initializeCrashHandler() {
        try {
            Logging.info("OneSignal: Initializing crash handler early...")
            Logging.info("OneSignal: Creating crash handler with minimal dependencies...")

            // Create crash handler directly (non-blocking, doesn't require services upfront)
            val logger = AndroidOtelLogger()
            val crashHandler: IOtelCrashHandler = OtelFactory.createCrashHandler(platformProvider, logger)

            Logging.info("OneSignal: Crash handler created, initializing...")
            crashHandler.initialize()

            // Log crash storage location for debugging
            Logging.info("OneSignal: ✅ Crash handler initialized successfully and ready to capture crashes")
            Logging.info("OneSignal: 📁 Crash logs will be stored at: ${platformProvider.crashStoragePath}")
            Logging.info("OneSignal: 💡 To view crash logs, use: adb shell run-as ${platformProvider.appPackageId} ls -la ${platformProvider.crashStoragePath}")

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
                // If ANR detector initialization fails, log it but don't crash
                Logging.error("OneSignal: Failed to initialize ANR detector: ${e.message}", e)
            }
        } catch (e: Exception) {
            // If crash handler initialization fails, log it but don't crash
            Logging.error("OneSignal: Failed to initialize crash handler: ${e.message}", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun initializeOtelLogging() {
        // Initialize Otel logging asynchronously to avoid blocking initialization
        // Remote logging is not critical for crashes, so it's safe to do this in the background
        // To remove the variable of refactors of internal dependencies breaking this
        // initialization, OtelIdResolver reads directly from SharedPreferences, to fully decouple
        // from our service architecture.
        suspendifyOnIO {
            try {
                // Get the remote log level as string (defaults to "ERROR" if null, "NONE" if explicitly set)
                val remoteLogLevelStr = platformProvider.remoteLogLevel

                if (remoteLogLevelStr != null && remoteLogLevelStr != "NONE") {
                    Logging.debug("OneSignal: Remote logging enabled at level $remoteLogLevelStr, initializing Otel logging integration...")
                    val remoteTelemetry = OtelFactory.createRemoteTelemetry(platformProvider)

                    val remoteLogLevel =
                        try {
                            LogLevel.valueOf(remoteLogLevelStr)
                        } catch (_: Exception) {
                            LogLevel.NONE
                        }

                    val shouldSendLogLevel: (LogLevel) -> Boolean = { level ->
                        when {
                            remoteLogLevel == LogLevel.NONE -> false
                            else -> level <= remoteLogLevel
                        }
                    }

                    Logging.setOtelTelemetry(remoteTelemetry, shouldSendLogLevel)
                    Logging.debug("OneSignal: ✅ Otel logging integration initialized - logs at level $remoteLogLevelStr and above will be sent to remote server")
                } else {
                    Logging.debug("OneSignal: ❌ Remote logging disabled (level: $remoteLogLevelStr), skipping Otel logging integration")
                }
            } catch (t: Throwable) {
                // Never crash the app if Otel fails to initialize.
                Logging.warn("OneSignal: Failed to initialize Otel logging: ${t.message}", t)
            }
        }
    }
}
