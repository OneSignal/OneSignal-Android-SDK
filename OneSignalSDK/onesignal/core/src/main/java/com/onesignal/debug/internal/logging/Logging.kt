package com.onesignal.debug.internal.logging

import android.app.AlertDialog
import com.onesignal.common.threading.suspendifyOnMain
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.ILogListener
import com.onesignal.debug.LogLevel
import com.onesignal.debug.OneSignalLogEvent
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.CopyOnWriteArraySet

object Logging {
    private const val TAG = "OneSignal"

    var applicationService: IApplicationService? = null

    private val logListeners = CopyOnWriteArraySet<ILogListener>()

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

    fun addListener(listener: ILogListener) {
        logListeners.add(listener)
    }

    fun removeListener(listener: ILogListener) {
        logListeners.remove(listener)
    }
}
