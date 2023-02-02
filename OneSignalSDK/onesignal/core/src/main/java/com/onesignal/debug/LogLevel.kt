package com.onesignal.debug

enum class LogLevel {
    NONE,
    FATAL,
    ERROR,
    WARN,
    INFO,
    DEBUG,
    VERBOSE;

    companion object {
        @JvmStatic
        fun fromInt(value: Int) : LogLevel {
            return values()[value];
        }
    }
}
