/**
 * Modified MIT License
 *
 * Copyright 2020 OneSignal
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
package com.onesignal.core.internal.background.impl

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.onesignal.common.AndroidSupportV4Compat
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.background.IBackgroundManager
import com.onesignal.core.internal.background.IBackgroundService
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.time.ITime
import com.onesignal.core.services.SyncJobService
import com.onesignal.core.services.SyncService
import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * This schedules a job to fire later in the background preform player REST calls to make a(n);
 * - on_focus
 * - Delayed by MIN_ON_SESSION_TIME so we know no more time can be attributed to current session
 * - Location update
 * - Player update
 * - IF there are any pending field updates - pushToken, tags, etc
 */
internal class BackgroundManager(
    private val _applicationService: IApplicationService,
    private val _time: ITime,
    private val _backgroundServices: List<(IBackgroundService)>
) : IApplicationLifecycleHandler, IBackgroundManager, IStartableService {

    override var needsJobReschedule = false

    private val _lock = Any()
    private var _nextScheduledSyncTimeMs = 0L
    private var backgroundSyncJob: Job? = null

    @SuppressLint("NewApi") // can suppress because we are only retrieving the class, not necessarily using it
    private val syncServiceJobClass: Class<*> = SyncJobService::class.java
    private val syncServicePendingIntentClass: Class<*> = SyncService::class.java

    override fun start() {
        _applicationService.addApplicationLifecycleHandler(this)
    }

    override fun onFocus() {
        cancelSyncTask()
    }

    override fun onUnfocused() {
        scheduleBackground()
    }

    private fun scheduleBackground() {
        // Go through the background services to figure out whether, and when, to schedule the background run.
        var minDelay: Long? = null

        for (backgroundService in _backgroundServices) {
            val scheduleIn = backgroundService.scheduleBackgroundRunIn
            if (scheduleIn != null) {
                if (minDelay == null || scheduleIn < minDelay) {
                    minDelay = scheduleIn
                }
            }
        }

        if (minDelay != null) {
            scheduleSyncTask(minDelay)
        }
    }

    private fun cancelSyncTask() {
        synchronized(_lock) {
            _nextScheduledSyncTimeMs = 0L
            cancelBackgroundSyncTask()
        }
    }

    // Entry point from SyncJobService and SyncService when the job is kicked off
    override suspend fun runBackgroundServices() = coroutineScope {
        Logging.debug("OSBackground sync, calling initWithContext")

        backgroundSyncJob = launch(Dispatchers.Unconfined) {
            synchronized(_lock) { _nextScheduledSyncTimeMs = 0L }

            for (backgroundService in _backgroundServices) {
                backgroundService.backgroundRun()
            }

            scheduleBackground()
        }
    }

    override fun cancelRunBackgroundServices(): Boolean {
        if (backgroundSyncJob == null) return false
        if (!backgroundSyncJob!!.isActive) return false
        backgroundSyncJob!!.cancel()
        return true
    }

    private fun scheduleSyncTask(delayMs: Long) {
        var delayMs = delayMs
        synchronized(_lock) {
            if (_nextScheduledSyncTimeMs != 0L &&
                _time.currentTimeMillis + delayMs > _nextScheduledSyncTimeMs
            ) {
                Logging.verbose("OSSyncService scheduleSyncTask already update scheduled nextScheduledSyncTimeMs: $_nextScheduledSyncTimeMs")
                return
            }
            if (delayMs < 5000) delayMs = 5000
            scheduleBackgroundSyncTask(delayMs)
            _nextScheduledSyncTimeMs = _time.currentTimeMillis + delayMs
        }
    }

    private fun scheduleBackgroundSyncTask(delayMs: Long) {
        synchronized(_lock) {
            if (useJob()) {
                scheduleSyncServiceAsJob(delayMs)
            } else {
                scheduleSyncServiceAsAlarm(delayMs)
            }
        }
    }

    private fun hasBootPermission(): Boolean {
        return AndroidSupportV4Compat.ContextCompat.checkSelfPermission(
            _applicationService.appContext,
            "android.permission.RECEIVE_BOOT_COMPLETED"
        ) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun isJobIdRunning(): Boolean {
        val jobScheduler = _applicationService.appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        for (jobInfo in jobScheduler.allPendingJobs) {
            if (jobInfo.id == SYNC_TASK_ID && backgroundSyncJob != null && backgroundSyncJob!!.isActive) {
                return true
            }
        }
        return false
    }

    @RequiresApi(21)
    private fun scheduleSyncServiceAsJob(delayMs: Long) {
        Logging.verbose("OSBackgroundSync scheduleSyncServiceAsJob:atTime: $delayMs")
        if (isJobIdRunning()) {
            Logging.verbose("OSBackgroundSync scheduleSyncServiceAsJob Scheduler already running!")
            // If a JobScheduler is schedule again while running it will stop current job. We will schedule again when finished.
            // This will avoid InterruptionException due to thread.join() or queue.take() running.
            needsJobReschedule = true
            return
        }
        val jobBuilder = JobInfo.Builder(SYNC_TASK_ID, ComponentName(_applicationService.appContext!!, syncServiceJobClass!!))
        jobBuilder
            .setMinimumLatency(delayMs)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        if (hasBootPermission()) jobBuilder.setPersisted(true)
        val jobScheduler = _applicationService.appContext!!.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        try {
            val result = jobScheduler.schedule(jobBuilder.build())
            Logging.info("OSBackgroundSync scheduleSyncServiceAsJob:result: $result")
        } catch (e: NullPointerException) {
            // Catch for buggy Oppo devices
            // https://github.com/OneSignal/OneSignal-Android-SDK/issues/487
            Logging.error(
                "scheduleSyncServiceAsJob called JobScheduler.jobScheduler which " +
                    "triggered an internal null Android error. Skipping job.",
                e
            )
        }
    }

    private fun scheduleSyncServiceAsAlarm(delayMs: Long) {
        Logging.verbose(this.javaClass.simpleName + " scheduleServiceSyncTask:atTime: " + delayMs)
        val pendingIntent = syncServicePendingIntent()
        val alarm = _applicationService.appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAtMs = _time.currentTimeMillis + delayMs
        alarm[AlarmManager.RTC_WAKEUP, triggerAtMs] = pendingIntent
    }

    private fun cancelBackgroundSyncTask() {
        Logging.debug(this.javaClass.simpleName + " cancel background sync")

        synchronized(_lock) {
            if (useJob()) {
                val jobScheduler = _applicationService.appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                jobScheduler.cancel(SYNC_TASK_ID)
            } else {
                val alarmManager = _applicationService.appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(syncServicePendingIntent())
            }
        }
    }

    private fun syncServicePendingIntent(): PendingIntent {
        // KEEP - PendingIntent.FLAG_UPDATE_CURRENT
        //          Some Samsung devices will throw the below exception otherwise.
        //          "java.lang.SecurityException: !@Too many alarms (500) registered"
        return PendingIntent.getService(
            _applicationService.appContext,
            SYNC_TASK_ID,
            Intent(_applicationService.appContext, syncServicePendingIntentClass),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun useJob(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    }

    companion object {
        private const val SYNC_TASK_ID = 2071862118
    }
}
