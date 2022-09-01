package com.onesignal.core.internal.time

interface ITime {
    val currentTimeMillis: Long
    val elapsedRealtime: Long
}
