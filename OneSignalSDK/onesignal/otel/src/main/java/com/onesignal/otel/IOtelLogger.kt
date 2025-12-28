package com.onesignal.otel

/**
 * Platform-agnostic logger interface for the Otel module.
 * Implementations should be provided by the platform (Android/iOS).
 */
interface IOtelLogger {
    /**
     * Logs an error message.
     *
     * @param message The error message to log
     */
    fun error(message: String)

    /**
     * Logs a warning message.
     *
     * @param message The warning message to log
     */
    fun warn(message: String)

    /**
     * Logs an informational message.
     *
     * @param message The info message to log
     */
    fun info(message: String)

    /**
     * Logs a debug message.
     *
     * @param message The debug message to log
     */
    fun debug(message: String)
}
