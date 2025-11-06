package com.onesignal.debug.internal.crash

internal interface IOneSignalCrashReporter {
    suspend fun saveCrash(thread: Thread, throwable: Throwable)
}
