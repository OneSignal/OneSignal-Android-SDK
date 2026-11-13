package com.onesignal.example.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Logging for the demo app. Marks every line with `[OneSignal]`.
 *
 * ```
 * DemoLog.d("Sending notification: Simple")
 * // D/OneSignalDemo: [OneSignal] Sending notification: Simple
 * ```
 *
 * [show] also appends the message to the in-app Log View, which the shared Appium suite reads.
 *
 * SDK output that MainApplication's log listener forwards does not come through here. Those
 * lines belong to the SDK, and marking them would bury the demo's own output when you grep.
 */
object DemoLog {
    private const val TAG = "OneSignalDemo"
    private const val MESSAGE_PREFIX = "[OneSignal]"
    private const val MAX_ENTRIES = 100

    data class Entry(val id: Long, val time: String, val message: String)

    // In memory only, so every launch (including a cold start from a notification tap) starts empty.
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()
    private val nextId = AtomicLong()

    fun v(message: String) = Log.v(TAG, formatMessage(message))

    fun d(message: String) = Log.d(TAG, formatMessage(message))

    fun i(message: String) = Log.i(TAG, formatMessage(message))

    fun w(message: String) = Log.w(TAG, formatMessage(message))

    fun e(message: String) = Log.e(TAG, formatMessage(message))

    fun e(message: String, throwable: Throwable) =
        Log.e(TAG, formatMessage(message), throwable)

    fun show(message: String) {
        i(message)
        val entry = Entry(
            id = nextId.getAndIncrement(),
            time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
            message = message,
        )
        _entries.update { (it + entry).takeLast(MAX_ENTRIES) }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    private fun formatMessage(message: String): String =
        if (message.startsWith(MESSAGE_PREFIX)) message else "$MESSAGE_PREFIX $message"
}
