package com.onesignal.debug.internal.logging

import android.app.AlertDialog
import android.os.Build
import com.onesignal.common.threading.suspendifyOnMain
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.ILogListener
import com.onesignal.debug.LogLevel
import com.onesignal.debug.OneSignalLogEvent
import com.onesignal.otel.IOtelOpenTelemetryRemote
import com.onesignal.otel.OtelLoggingHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.CopyOnWriteArraySet

object Logging {
    private const val TAG = "OneSignal"

    var applicationService: IApplicationService? = null
    var androidVersion: Int = Build.VERSION.SDK_INT

    private val logListeners = CopyOnWriteArraySet<ILogListener>()

    /**
     * Optional Otel remote telemetry for logging SDK events.
     * Set this when remote logging is enabled.
     */
    @Volatile
    private var otelRemoteTelemetry: IOtelOpenTelemetryRemote? = null

    /**
     * Function to check if a specific log level should be sent remotely.
     * Set this to dynamically check remote logging configuration based on log level.
     */
    @Volatile
    private var shouldSendLogLevel: (LogLevel) -> Boolean = { false }

    /**
     * Sets the Otel remote telemetry instance and log level check function.
     * This should be called when remote logging is enabled.
     *
     * @param telemetry The Otel remote telemetry instance
     * @param shouldSend Function that returns true if a log level should be sent remotely
     */
    fun setOtelTelemetry(
        telemetry: IOtelOpenTelemetryRemote?,
        shouldSend: (LogLevel) -> Boolean = { false },
    ) {
        otelRemoteTelemetry = telemetry
        shouldSendLogLevel = shouldSend
    }

    // Coroutine scope for async Otel logging (non-blocking)
    private val otelLoggingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @JvmStatic
    var logLevel = LogLevel.WARN

    @JvmStatic
    var visualLogLevel = LogLevel.NONE

    @JvmStatic
    fun atLogLevel(level: LogLevel): Boolean = level.compareTo(visualLogLevel) < 1 || level.compareTo(logLevel) < 1

    @JvmStatic
    fun verbose(
        message: String,
        throwable: Throwable? = null,
    ) {
        log(LogLevel.VERBOSE, message, throwable)
    }

    @JvmStatic
    fun debug(
        message: String,
        throwable: Throwable? = null,
    ) {
        log(LogLevel.DEBUG, message, throwable)
    }

    @JvmStatic
    fun info(
        message: String,
        throwable: Throwable? = null,
    ) {
        log(LogLevel.INFO, message, throwable)
    }

    @JvmStatic
    fun warn(
        message: String,
        throwable: Throwable? = null,
    ) {
        log(LogLevel.WARN, message, throwable)
    }

    @JvmStatic
    fun error(
        message: String,
        throwable: Throwable? = null,
    ) {
        log(LogLevel.ERROR, message, throwable)
    }

    @JvmStatic
    fun fatal(
        message: String,
        throwable: Throwable? = null,
    ) {
        log(LogLevel.FATAL, message, throwable)
    }

    @JvmStatic
    fun log(
        level: LogLevel,
        message: String,
    ) {
        log(level, message, null)
    }

    @JvmStatic
    fun log(
        level: LogLevel,
        message: String,
        throwable: Throwable?,
    ) {
        val fullMessage = "[${Thread.currentThread().name}] $message"

        logToLogcat(level, fullMessage, throwable)
        showVisualLogging(level, fullMessage, throwable)
        callLogListeners(level, fullMessage, throwable)
        logToOtel(level, fullMessage, throwable)
    }

    private fun logToLogcat(
        level: LogLevel,
        message: String,
        throwable: Throwable?,
    ) {
        if (level.compareTo(logLevel) >= 1) return
        when (level) {
            LogLevel.VERBOSE -> android.util.Log.v(TAG, message, throwable)
            LogLevel.DEBUG -> android.util.Log.d(TAG, message, throwable)
            LogLevel.INFO -> android.util.Log.i(TAG, message, throwable)
            LogLevel.WARN -> android.util.Log.w(TAG, message, throwable)
            LogLevel.ERROR, LogLevel.FATAL -> android.util.Log.e(TAG, message, throwable)
            else -> {}
        }
    }

    private fun showVisualLogging(
        level: LogLevel,
        message: String,
        throwable: Throwable?,
    ) {
        if (level.compareTo(visualLogLevel) >= 1) return

        try {
            var fullMessage: String? = "$message\n".trimIndent()
            if (throwable != null) {
                fullMessage += throwable.message
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                fullMessage += sw.toString()
            }
            val finalFullMessage = fullMessage

            suspendifyOnMain {
                val currentActivity = applicationService?.current
                if (currentActivity != null) {
                    AlertDialog
                        .Builder(currentActivity)
                        .setTitle(level.toString())
                        .setMessage(finalFullMessage)
                        .show()
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "Error showing logging message.", t)
        }
    }

    private fun callLogListeners(
        level: LogLevel,
        message: String,
        throwable: Throwable?,
    ) {
        if (logListeners.isEmpty()) return

        var logEntry = message
        if (throwable != null) {
            logEntry += "\n" + android.util.Log.getStackTraceString(throwable)
        }
        for (listener in logListeners) {
            listener.onLogEvent(OneSignalLogEvent(level, logEntry))
        }
    }

    /**
     * Logs to Otel remote telemetry if enabled.
     * This is non-blocking and runs asynchronously.
     */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private fun logToOtel(
        level: LogLevel,
        message: String,
        throwable: Throwable?,
    ) {
        val telemetry = otelRemoteTelemetry ?: return

        // Skip NONE level
        if (level == LogLevel.NONE) return

        // Check if this log level should be sent remotely
        if (!shouldSendLogLevel(level)) return

        // Otel library requires Android Oreo (8) or newer
        if (androidVersion < Build.VERSION_CODES.O) return

        // Log asynchronously (non-blocking)
        otelLoggingScope.launch {
            try {
                OtelLoggingHelper.logToOtel(
                    telemetry = telemetry,
                    level = level.name,
                    message = message,
                    exceptionType = throwable?.javaClass?.name,
                    exceptionMessage = throwable?.message,
                    exceptionStacktrace = throwable?.stackTraceToString(),
                )
            } catch (e: Throwable) {
                // Don't log Otel errors to Otel (would cause infinite loop)
                // Just print to logcat
                android.util.Log.e(TAG, "Failed to log to Otel: ${e.message}", e)
            }
        }
    }

    fun addListener(listener: ILogListener) {
        logListeners.add(listener)
    }

    fun removeListener(listener: ILogListener) {
        logListeners.remove(listener)
    }
}
