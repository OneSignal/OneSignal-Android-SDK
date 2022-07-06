package com.onesignal.onesignal.internal.common.time

interface ITime {
    val currentTimeMillis: Long
    val elapsedRealtime: Long
}