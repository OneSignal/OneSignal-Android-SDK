package com.onesignal.onesignal.logging

import android.app.AlertDialog
import java.io.PrintWriter
import java.io.StringWriter

// This is singleton class that is designed to make OneSignal easy to use.
//    - No instance management is required from the app developer.
// This is a wrapper around an instance of OneSignalImp, no logic lives in this class
object Logging {
    private const val TAG = "OneSignal"

    var logLevel = LogLevel.WARN

    fun Verbose() {

    }

    fun log(level: LogLevel, message: String) {
        log(level, message, null);
    }

    fun log(level: LogLevel, message: String, throwable: Throwable?) {
        if (level.compareTo(logLevel) < 1) {
            if (level == LogLevel.VERBOSE)
                android.util.Log.v(TAG, message, throwable)
            else if (level == LogLevel.DEBUG)
                android.util.Log.d(TAG, message, throwable)
            else if (level == LogLevel.INFO)
                android.util.Log.i(TAG, message, throwable)
            else if (level == LogLevel.WARN)
                android.util.Log.w(TAG, message, throwable)
            else if (level == LogLevel.ERROR || level == LogLevel.FATAL)
                android.util.Log.e(TAG, message, throwable)
        }

        if (level.compareTo(logLevel) < 1 && OneSignal.getCurrentActivity() != null) {
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
                OSUtils.runOnMainUIThread(Runnable {
                    if (OneSignal.getCurrentActivity() != null) AlertDialog.Builder(OneSignal.getCurrentActivity())
                            .setTitle(level.toString())
                            .setMessage(finalFullMessage)
                            .show()
                })
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "Error showing logging message.", t)
            }
        }
    }
}
