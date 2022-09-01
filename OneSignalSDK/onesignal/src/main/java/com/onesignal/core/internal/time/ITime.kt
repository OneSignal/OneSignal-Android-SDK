package com.onesignal.core.internal.time

internal interface ITime {
    val currentTimeMillis: Long
    val elapsedRealtime: Long
}
