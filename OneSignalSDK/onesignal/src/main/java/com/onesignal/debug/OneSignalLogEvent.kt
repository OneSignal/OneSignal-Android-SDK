package com.onesignal.debug

import com.onesignal.OneSignal

data class OneSignalLogEvent(
    val level: OneSignal.LOG_LEVEL,
    val entry: String,
)
