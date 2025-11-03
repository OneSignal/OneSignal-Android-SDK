package com.onesignal.debug.internal.crash

internal interface IOneSignalCrashReporter {
    suspend fun sendCrash(thread: Thread, throwable: Throwable)
}
