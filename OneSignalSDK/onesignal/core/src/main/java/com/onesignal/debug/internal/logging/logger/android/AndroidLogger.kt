package com.onesignal.debug.internal.logging.logger.android

import com.onesignal.debug.internal.logging.Logging
import com.onesignal.logger.ILogger

/** Android [ILogger], delegating to the existing [Logging] object. */
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
