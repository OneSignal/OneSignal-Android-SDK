package com.onesignal

import android.os.Process

internal open class BackgroundRunnable : Runnable {
    override fun run() {
        Thread.currentThread().priority = Process.THREAD_PRIORITY_BACKGROUND
    }
}