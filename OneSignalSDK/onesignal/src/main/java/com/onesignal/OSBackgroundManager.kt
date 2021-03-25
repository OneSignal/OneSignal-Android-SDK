package com.onesignal

open class OSBackgroundManager {

    fun runRunnableOnThread(runnable: Runnable, threadName: String) {
        // DB access is a heavy task, dispatch to a thread if running on main thread
        if (OSUtils.isRunningOnMainThread()) Thread(runnable, threadName).start() else runnable.run()
    }
}