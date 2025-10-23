package com.onesignal.debug.internal.logging

class CrashDetails internal constructor(
    /** Returns the thread that crashed.  */
    val thread: Thread,
    /** Returns the cause of the crash.  */
    val cause: Throwable,
)