package com.onesignal.sdktest.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pass-through log manager that both displays logs in the UI and forwards to Android's logcat.
 * Use this instead of android.util.Log to get both logcat output and UI display.
 */
object LogManager {
    
    private const val TAG = "OneSignalDemo"
    private const val MAX_LOGS = 100
    
    private val _logs = mutableStateListOf<LogEntry>()
    val logs: List<LogEntry> get() = _logs
    
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val mainHandler = Handler(Looper.getMainLooper())
    
    /**
     * Log with custom tag (used by SDK log listener)
     */
    fun log(tag: String, message: String, level: LogLevel) {
        // Forward to Android logcat (can happen on any thread)
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.WARN -> Log.w(tag, message)
            LogLevel.ERROR -> Log.e(tag, message)
        }
        
        // Create entry with current timestamp
        val entry = LogEntry(
            timestamp = timeFormat.format(Date()),
            message = message,
            level = level
        )
        
        // Add to UI log list on main thread (required for Compose state)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            addLogEntry(entry)
        } else {
            mainHandler.post { addLogEntry(entry) }
        }
    }
    
    private fun addLogEntry(entry: LogEntry) {
        _logs.add(0, entry) // Add to beginning (newest first)
        
        // Keep only the last MAX_LOGS entries
        while (_logs.size > MAX_LOGS) {
            _logs.removeAt(_logs.lastIndex)
        }
    }
    
    // Convenience methods with default tag
    fun d(message: String) = log(TAG, message, LogLevel.DEBUG)
    fun i(message: String) = log(TAG, message, LogLevel.INFO)
    fun w(message: String) = log(TAG, message, LogLevel.WARN)
    fun e(message: String) = log(TAG, message, LogLevel.ERROR)
    
    // Methods with custom tag (mimics android.util.Log API)
    fun d(tag: String, message: String) = log(tag, message, LogLevel.DEBUG)
    fun i(tag: String, message: String) = log(tag, message, LogLevel.INFO)
    fun w(tag: String, message: String) = log(tag, message, LogLevel.WARN)
    fun e(tag: String, message: String) = log(tag, message, LogLevel.ERROR)
    
    // Methods with throwable (mimics android.util.Log API)
    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
        log(tag, "$message: ${throwable.message}", LogLevel.ERROR)
    }
    
    fun w(tag: String, message: String, throwable: Throwable) {
        Log.w(tag, message, throwable)
        log(tag, "$message: ${throwable.message}", LogLevel.WARN)
    }
    
    // Legacy methods for compatibility
    fun info(message: String) = i(message)
    fun debug(message: String) = d(message)
    fun warn(message: String) = w(message)
    fun error(message: String) = e(message)
    
    fun clear() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _logs.clear()
        } else {
            mainHandler.post { _logs.clear() }
        }
    }
}

data class LogEntry(
    val timestamp: String,
    val message: String,
    val level: LogLevel
)

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}
