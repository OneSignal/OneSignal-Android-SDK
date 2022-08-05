package com.onesignal.onesignal.core.internal.time

import android.os.SystemClock

class Time : ITime {
    override val currentTimeMillis: Long
        get() = System.currentTimeMillis()
    override val elapsedRealtime: Long
        get() = SystemClock.elapsedRealtime()
}