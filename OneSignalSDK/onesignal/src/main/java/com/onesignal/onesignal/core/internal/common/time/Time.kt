package com.onesignal.onesignal.core.internal.common.time

import android.os.SystemClock

class Time : ITime {
    override val currentTimeMillis: Long
        get() = System.currentTimeMillis()
    override val elapsedRealtime: Long
        get() = SystemClock.elapsedRealtime()
}