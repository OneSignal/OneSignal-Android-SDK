package com.onesignal.debug.internal.logging.otel

interface IOneSignalCrashConfigProvider {
    val path: String

    val minFileAgeForReadMillis: Long
}
