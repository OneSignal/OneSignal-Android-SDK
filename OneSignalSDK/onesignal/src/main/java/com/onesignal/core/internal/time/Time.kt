package com.onesignal.core.internal.time

import android.os.SystemClock

internal class Time : ITime {
    override val currentTimeMillis: Long
        get() = System.currentTimeMillis()
    override val elapsedRealtime: Long
        get() = SystemClock.elapsedRealtime()
}
