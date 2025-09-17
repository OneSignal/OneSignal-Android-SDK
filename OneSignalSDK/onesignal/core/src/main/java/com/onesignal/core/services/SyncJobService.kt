/**
 * Modified MIT License
 *
 * Copyright 2018 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.onesignal.core.services

import android.app.job.JobParameters
import android.app.job.JobService
import com.onesignal.OneSignal
import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.core.internal.background.IBackgroundManager
import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class SyncJobService : JobService() {
    override fun onStartJob(jobParameters: JobParameters): Boolean {
        runBlocking {
            OneSignal.ensureOneSignalInitialized(this)
        }
        

        suspendifyOnThread {
            if (!OneSignal.initWithContext(this)) {
                withContext(Dispatchers.Main) {
                    jobFinished(jobParameters, false)
                }
                return@suspendifyOnThread
            }

            val backgroundService = OneSignal.getService<IBackgroundManager>()

            // Reschedule if needed
            val reschedule = backgroundService.needsJobReschedule
            backgroundService.needsJobReschedule = false

            withContext(Dispatchers.Main) {
                jobFinished(jobParameters, reschedule)
            }
        }

        return true
    }

    override fun onStopJob(jobParameters: JobParameters): Boolean {
        // We assume init has been called via onStartJob
        var backgroundService = OneSignal.getService<IBackgroundManager>()
        val reschedule = backgroundService.cancelRunBackgroundServices()
        Logging.debug("SyncJobService onStopJob called, system conditions not available reschedule: $reschedule")
        return reschedule
    }
}
