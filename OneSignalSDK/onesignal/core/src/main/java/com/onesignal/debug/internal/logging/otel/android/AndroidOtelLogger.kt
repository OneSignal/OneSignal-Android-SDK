package com.onesignal.debug.internal.logging.otel.android

import com.onesignal.debug.internal.logging.Logging
import com.onesignal.otel.IOtelLogger

/**
 * Android-specific implementation of IOtelLogger.
 * Delegates to the existing Logging object.
 */
internal class AndroidOtelLogger : IOtelLogger {
    override fun error(message: String) {
        Logging.error(message)
    }

    override fun warn(message: String) {
        Logging.warn(message)
    }

    override fun info(message: String) {
        Logging.info(message)
    }

    override fun debug(message: String) {
        Logging.debug(message)
    }
}
