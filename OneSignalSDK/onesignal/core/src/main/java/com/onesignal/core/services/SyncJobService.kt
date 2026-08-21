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
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.common.threading.launchOnIO
import com.onesignal.core.internal.background.IBackgroundManager
import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicReference

class SyncJobService : JobService() {
    private enum class RunState {
        RUNNING,
        STOPPED,
        FINISHED,
    }

    private class JobRun(val parameters: JobParameters) {
        val jobId = parameters.jobId
        val state = AtomicReference(RunState.RUNNING)
        val job = AtomicReference<Job?>()
    }

    private val activeRun = AtomicReference<JobRun?>()

    override fun onStartJob(jobParameters: JobParameters): Boolean {
        // Android delivers JobService.onStartJob on the main thread. The suspendifyOnIO call
        // below is the SDK's first IO-pool consumer on cold start in this process, and the
        // executor + dispatcher + coroutine-scope lazy chain it triggers was producing multi-
        // second main-thread blocks in production (SDK-4507). prewarm() shifts that cost to a
        // short-lived daemon thread; idempotent, so it's harmless when initWithContext already
        // ran. Must happen BEFORE suspendifyOnIO; calling it inside the dispatched block would
        // be too late because the cold-init cost has already been paid on entry to the helper.
        OneSignalDispatchers.prewarm()

        val run = JobRun(jobParameters)
        activeRun.getAndSet(run)?.let { previous ->
            previous.state.compareAndSet(RunState.RUNNING, RunState.STOPPED)
            previous.job.get()?.cancel()
        }
        val job = launchOnIO { executeRun(run) }
        run.job.set(job)
        if (run.state.get() == RunState.STOPPED) {
            job.cancel()
        }

        return true
    }

    private suspend fun executeRun(run: JobRun) {
        var reschedule = false
        try {
            if (!OneSignal.initWithContext(this)) {
                return
            }

            val backgroundService = OneSignal.getService<IBackgroundManager>()
            backgroundService.runBackgroundServices()
            reschedule = backgroundService.needsJobReschedule
            backgroundService.needsJobReschedule = false
            Logging.debug("LollipopSyncRunnable:JobFinished needsJobReschedule: $reschedule")
        } catch (e: CancellationException) {
            reschedule = true
            throw e
        } catch (e: Exception) {
            reschedule = true
            Logging.error("SyncJobService background execution failed", e)
        } finally {
            if (run.state.compareAndSet(RunState.RUNNING, RunState.FINISHED)) {
                activeRun.compareAndSet(run, null)
                jobFinished(run.parameters, reschedule)
            }
        }
    }

    override fun onStopJob(jobParameters: JobParameters): Boolean {
        val run = activeRun.get()
        val stopped =
            run != null &&
                run.jobId == jobParameters.jobId &&
                run.state.compareAndSet(RunState.RUNNING, RunState.STOPPED)
        if (stopped) {
            activeRun.compareAndSet(run, null)
            run?.job?.get()?.cancel()
        }
        return stopped
    }
}
