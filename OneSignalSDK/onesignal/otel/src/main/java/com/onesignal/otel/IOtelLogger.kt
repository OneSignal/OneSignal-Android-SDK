package com.onesignal.otel

/**
 * Platform-agnostic logger interface for the Otel module.
 * Implementations should be provided by the platform (Android/iOS).
 */
interface IOtelLogger {
    fun error(message: String)
    fun warn(message: String)
    fun info(message: String)
    fun debug(message: String)
}
