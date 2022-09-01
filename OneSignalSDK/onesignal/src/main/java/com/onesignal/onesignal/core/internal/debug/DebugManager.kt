package com.onesignal.onesignal.core.internal.debug

import com.onesignal.onesignal.core.IDebugManager
import com.onesignal.onesignal.core.LogLevel
import com.onesignal.onesignal.core.internal.logging.Logging

class DebugManager() : IDebugManager {
    override var logLevel: LogLevel
        get() = Logging.logLevel
        set(value) { Logging.logLevel = value }

    override var alertLevel: LogLevel
        get() = Logging.visualLogLevel
        set(value) { Logging.visualLogLevel = value }
}
