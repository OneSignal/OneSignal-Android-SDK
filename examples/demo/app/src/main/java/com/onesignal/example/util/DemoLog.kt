package com.onesignal.example.util

import android.util.Log

/**
 * Logging for the demo app. Marks every line with `[OneSignal]`.
 *
 * ```
 * DemoLog.d("Sending notification: Simple")
 * // D/OneSignalDemo: [OneSignal] Sending notification: Simple
 * ```
 *
 * SDK output that MainApplication's log listener forwards does not come through here. Those
 * lines belong to the SDK, and marking them would bury the demo's own output when you grep.
 */
object DemoLog {
    private const val TAG = "OneSignalDemo"
    private const val MESSAGE_PREFIX = "[OneSignal]"

    fun v(message: String) = Log.v(TAG, formatMessage(message))

    fun d(message: String) = Log.d(TAG, formatMessage(message))

    fun i(message: String) = Log.i(TAG, formatMessage(message))

    fun w(message: String) = Log.w(TAG, formatMessage(message))

    fun e(message: String) = Log.e(TAG, formatMessage(message))

    fun e(message: String, throwable: Throwable) =
        Log.e(TAG, formatMessage(message), throwable)

    private fun formatMessage(message: String): String =
        if (message.startsWith(MESSAGE_PREFIX)) message else "$MESSAGE_PREFIX $message"
}
