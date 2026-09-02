package com.onesignal.example.util

import android.util.Log

/**
 * Logging for the demo app. Marks both halves of every line with `[Demo]`, so `logcat -s` can
 * filter on the tag and a line is still recognizable when only the message column is in view.
 *
 * ```
 * DemoLog.d(TAG, "Sending notification: Simple")
 * // D/[Demo]MainViewModel: [Demo] Sending notification: Simple
 * ```
 *
 * Pass the plain class name as the tag. This adds the prefix.
 *
 * SDK output that MainApplication's log listener forwards does not come through here. Those
 * lines belong to the SDK, and marking them would bury the demo's own output when you grep.
 */
object DemoLog {
    private const val PREFIX = "[Demo]"

    fun v(tag: String, message: String) = Log.v(PREFIX + tag, "$PREFIX $message")

    fun d(tag: String, message: String) = Log.d(PREFIX + tag, "$PREFIX $message")

    fun i(tag: String, message: String) = Log.i(PREFIX + tag, "$PREFIX $message")

    fun w(tag: String, message: String) = Log.w(PREFIX + tag, "$PREFIX $message")

    fun e(tag: String, message: String) = Log.e(PREFIX + tag, "$PREFIX $message")

    fun e(tag: String, message: String, throwable: Throwable) =
        Log.e(PREFIX + tag, "$PREFIX $message", throwable)
}
