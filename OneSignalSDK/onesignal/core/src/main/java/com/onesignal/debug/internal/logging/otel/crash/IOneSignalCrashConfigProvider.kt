package com.onesignal.debug.internal.logging.otel.crash

internal interface IOneSignalCrashConfigProvider {
    val path: String

    val minFileAgeForReadMillis: Long
}
