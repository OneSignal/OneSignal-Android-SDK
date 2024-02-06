package com.onesignal.location.internal.common

internal object LocationConstants {
    // Testing values
//    const val TIME_FOREGROUND_SEC = (.6 * 60).toLong()
//    const val TIME_BACKGROUND_SEC = (1 * 60).toLong()

    const val TIME_FOREGROUND_SEC = (5 * 60).toLong()
    const val TIME_BACKGROUND_SEC = (10 * 60).toLong()
    const val FOREGROUND_UPDATE_TIME_MS = (TIME_FOREGROUND_SEC - 30) * 1000
    const val BACKGROUND_UPDATE_TIME_MS = (TIME_BACKGROUND_SEC - 30) * 1000

    const val ANDROID_FINE_LOCATION_PERMISSION_STRING = "android.permission.ACCESS_FINE_LOCATION"
    const val ANDROID_COARSE_LOCATION_PERMISSION_STRING = "android.permission.ACCESS_COARSE_LOCATION"
    const val ANDROID_BACKGROUND_LOCATION_PERMISSION_STRING = "android.permission.ACCESS_BACKGROUND_LOCATION"
}
