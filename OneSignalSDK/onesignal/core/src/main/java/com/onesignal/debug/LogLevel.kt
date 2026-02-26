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

        /**
         * Parses a [LogLevel] from its string name (case-insensitive).
         * Returns `null` if the string is null or not a valid level name.
         */
        @JvmStatic
        fun fromString(value: String?): LogLevel? {
            if (value == null) return null
            return try {
                valueOf(value.uppercase())
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
