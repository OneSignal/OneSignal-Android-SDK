package com.onesignal.debug.internal

import com.onesignal.debug.IDebugManager
import com.onesignal.debug.ILogListener
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging

internal class DebugManager() : IDebugManager {
    override var logLevel: LogLevel
        get() = Logging.logLevel
        set(value) {
            Logging.logLevel = value
        }

    override var alertLevel: LogLevel
        get() = Logging.visualLogLevel
        set(value) {
            Logging.visualLogLevel = value
        }

    init {
        logLevel = LogLevel.WARN
        alertLevel = LogLevel.NONE
    }

    override fun addLogListener(listener: ILogListener) {
        Logging.addListener(listener)
    }

    override fun removeLogListener(listener: ILogListener) {
        Logging.removeListener(listener)
    }
}
