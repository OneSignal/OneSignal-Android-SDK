package com.onesignal.debug

data class OneSignalLogEvent(
    val level: LogLevel,
    val entry: String,
)
