package com.onesignal.core.internal.time.impl

import com.onesignal.core.internal.time.ITime

internal class Time : ITime {
    override val currentTimeMillis: Long
        get() = System.currentTimeMillis()
}
