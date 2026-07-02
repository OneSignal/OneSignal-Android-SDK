package com.onesignal.logger

/**
 * Platform-agnostic logger interface for the logger module.
 *
 * Mirrors `IOtelLogger`. Implementations are provided by the platform (on Android
 * this delegates to the core `Logging` object).
 */
interface ILogger {
    fun error(message: String)

    fun warn(message: String)

    fun info(message: String)

    fun debug(message: String)
}
