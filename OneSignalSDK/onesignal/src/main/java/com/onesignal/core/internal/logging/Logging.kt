package com.onesignal.core.internal.logging

import com.onesignal.core.debug.LogLevel

internal object Logging {
    private const val TAG = "OneSignal"

    @JvmStatic
    var logLevel = LogLevel.WARN
    @JvmStatic
    var visualLogLevel = LogLevel.WARN

    @JvmStatic
    fun atLogLevel(level: LogLevel): Boolean {
        return level.compareTo(visualLogLevel) < 1 || level.compareTo(logLevel) < 1
    }

    @JvmStatic
    fun verbose(message: String, throwable: Throwable? = null) {
        log(LogLevel.VERBOSE, message, throwable)
    }

    @JvmStatic
    fun debug(message: String, throwable: Throwable? = null) {
        log(LogLevel.DEBUG, message, throwable)
    }

    @JvmStatic
    fun info(message: String, throwable: Throwable? = null) {
        log(LogLevel.INFO, message, throwable)
    }

    @JvmStatic
    fun warn(message: String, throwable: Throwable? = null) {
        log(LogLevel.WARN, message, throwable)
    }

    @JvmStatic
    fun error(message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, message, throwable)
    }

    @JvmStatic
    fun fatal(message: String, throwable: Throwable? = null) {
        log(LogLevel.FATAL, message, throwable)
    }

    @JvmStatic
    fun log(level: LogLevel, message: String) {
        log(level, message, null)
    }

    @JvmStatic
    fun log(level: LogLevel, message: String, throwable: Throwable?) {
        if (level.compareTo(logLevel) < 1) {
            when (level) {
                LogLevel.VERBOSE -> android.util.Log.v(TAG, message, throwable)
                LogLevel.DEBUG -> android.util.Log.d(TAG, message, throwable)
                LogLevel.INFO -> android.util.Log.i(TAG, message, throwable)
                LogLevel.WARN -> android.util.Log.w(TAG, message, throwable)
                LogLevel.ERROR, LogLevel.FATAL -> android.util.Log.e(TAG, message, throwable)
            }
        }
// TODO: Implement
//        if (level.compareTo(logLevel) < 1 && OneSignal.getCurrentActivity() != null) {
//            try {
//                var fullMessage: String? = "$message\n".trimIndent()
//                if (throwable != null) {
//                    fullMessage += throwable.message
//                    val sw = StringWriter()
//                    val pw = PrintWriter(sw)
//                    throwable.printStackTrace(pw)
//                    fullMessage += sw.toString()
//                }
//                val finalFullMessage = fullMessage
//                OSUtils.runOnMainUIThread(Runnable {
//                    if (OneSignal.getCurrentActivity() != null) AlertDialog.Builder(OneSignal.getCurrentActivity())
//                            .setTitle(level.toString())
//                            .setMessage(finalFullMessage)
//                            .show()
//                })
//            } catch (t: Throwable) {
//                android.util.Log.e(TAG, "Error showing logging message.", t)
//            }
//        }
    }
}
