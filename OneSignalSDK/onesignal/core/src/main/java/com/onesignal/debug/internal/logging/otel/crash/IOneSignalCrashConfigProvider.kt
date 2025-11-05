package com.onesignal.debug.internal.logging.otel.crash

interface IOneSignalCrashConfigProvider {
    val path: String

    val minFileAgeForReadMillis: Long
}
