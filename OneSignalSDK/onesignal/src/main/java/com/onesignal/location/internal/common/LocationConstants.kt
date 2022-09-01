package com.onesignal.location.internal.common

internal object LocationConstants {
    // Testing values
//    const val TIME_FOREGROUND_SEC = (.6 * 60).toLong()
//    const val TIME_BACKGROUND_SEC = (1 * 60).toLong()

    const val TIME_FOREGROUND_SEC = (5 * 60).toLong()
    const val TIME_BACKGROUND_SEC = (10 * 60).toLong()
    const val FOREGROUND_UPDATE_TIME_MS = (TIME_FOREGROUND_SEC - 30) * 1000
    const val BACKGROUND_UPDATE_TIME_MS = (TIME_BACKGROUND_SEC - 30) * 1000
}
