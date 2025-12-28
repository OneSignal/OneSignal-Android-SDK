package com.onesignal.debug

/**
 * Log level enumeration for controlling the verbosity of OneSignal SDK logging.
 * Use [IDebugManager.logLevel] to set the desired log level for the SDK.
 *
 * Log levels are ordered from least to most verbose:
 * [NONE] < [FATAL] < [ERROR] < [WARN] < [INFO] < [DEBUG] < [VERBOSE]
 */
enum class LogLevel {
    /**
     * No logging will be output.
     */
    NONE,

    /**
     * Only fatal error messages will be logged.
     */
    FATAL,

    /**
     * Error and fatal messages will be logged.
     */
    ERROR,

    /**
     * Warning, error, and fatal messages will be logged. This is the default log level.
     */
    WARN,

    /**
     * Informational, warning, error, and fatal messages will be logged.
     */
    INFO,

    /**
     * Debug, informational, warning, error, and fatal messages will be logged.
     */
    DEBUG,

    /**
     * All messages including verbose output will be logged.
     */
    VERBOSE,
    ;

    companion object {
        @JvmStatic
        fun fromInt(value: Int): LogLevel {
            return values()[value]
        }
    }
}
