package com.onesignal.core.internal.debug

import com.onesignal.core.IDebugManager
import com.onesignal.core.LogLevel
import com.onesignal.core.internal.logging.Logging

class DebugManager() : IDebugManager {
    override var logLevel: LogLevel
        get() = Logging.logLevel
        set(value) { Logging.logLevel = value }

    override var alertLevel: LogLevel
        get() = Logging.visualLogLevel
        set(value) { Logging.visualLogLevel = value }
}
