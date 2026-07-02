package com.onesignal.debug.internal.logging.logger.android

import com.onesignal.debug.internal.logging.Logging
import com.onesignal.logger.ILogger

/**
 * Android implementation of [ILogger] for the `logger` module. Delegates to the
 * existing [Logging] object. Direct analogue of `AndroidOtelLogger`.
 */
internal class AndroidLogger : ILogger {
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
