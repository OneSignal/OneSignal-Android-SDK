/**
 * Modified MIT License
 * <p>
 * Copyright 2022 OneSignal
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.onesignal

import android.content.Context
import android.os.Handler
import androidx.work.*
import java.util.concurrent.TimeUnit

class OSFocusHandler {

    private var stopRunnable: Runnable? = null

    fun hasBackgrounded() = backgrounded

    fun hasCompleted() = completed

    fun startOnFocusWork() {
        resetBackgroundState()
        OneSignal.onesignalLog(
            OneSignal.LOG_LEVEL.DEBUG,
            "OSFocusHandler running onAppFocus"
        )
        OneSignal.onAppFocus()
    }

    fun startOnStartFocusWork() {
        if (stopped) {
            stopped = false
            stopRunnable = null
            OneSignal.onesignalLog(
                OneSignal.LOG_LEVEL.DEBUG,
                "OSFocusHandler running onAppStartFocusLogic"
            )
            OneSignal.onAppStartFocusLogic()
        } else {
            resetStopState()
        }
    }

    fun startOnStopFocusWork() {
        stopRunnable = Runnable {
            stopped = true
            OneSignal.onesignalLog(
                OneSignal.LOG_LEVEL.DEBUG,
                "OSFocusHandler setting stop state: true"
            )
        }.also {
            OSTimeoutHandler.getTimeoutHandler().startTimeout(stopDelay, it)
        }
    }

    fun startOnLostFocusWorker(tag: String, delay: Long, context: Context)  {
        val constraints = buildConstraints()
        val workRequest = OneTimeWorkRequest.Builder(OnLostFocusWorker::class.java)
            .setConstraints(constraints)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(tag)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                tag,
                ExistingWorkPolicy.KEEP,
                workRequest
            )
    }

    fun cancelOnLostFocusWorker(tag: String, context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(tag)
    }

    private fun resetStopState() {
        stopped = false
        stopRunnable?.let {
            OSTimeoutHandler.getTimeoutHandler().destroyTimeout(it)
        }
    }

    private fun resetBackgroundState() {
        resetStopState()
        backgrounded = false
    }

    private fun buildConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    class OnLostFocusWorker(context: Context, workerParams: WorkerParameters) :
        Worker(context, workerParams) {
        override fun doWork(): Result {
            onLostFocusDoWork()
            return Result.success()
        }
    }

    companion object {
        private const val stopDelay = 1500L
        private var stopped = false
        private var backgrounded = false
        private var completed = false

        fun onLostFocusDoWork() {
            val activityLifecycleHandler = ActivityLifecycleListener.getActivityLifecycleHandler()
            if (activityLifecycleHandler == null || activityLifecycleHandler.curActivity == null) {
                OneSignal.setInForeground(false)
            }
            OneSignal.onesignalLog(
                OneSignal.LOG_LEVEL.DEBUG,
                "OSFocusHandler running onAppLostFocus"
            )
            backgrounded = true
            OneSignal.onAppLostFocus()
            completed = true
        }
    }
}
