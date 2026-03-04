package com.onesignal.core.internal.time.impl

import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.onesignal.core.internal.time.ITime

internal class Time : ITime {
    override val currentTimeMillis: Long
        get() = System.currentTimeMillis()
    override val processUptimeMillis: Long
        @RequiresApi(Build.VERSION_CODES.N)
        get() = SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis()
}
