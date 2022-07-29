package com.onesignal.onesignal.core.internal.common.time

interface ITime {
    val currentTimeMillis: Long
    val elapsedRealtime: Long
}