package com.onesignal.onesignal.device.services

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.app.job.JobServiceEngine
import android.app.job.JobParameters
import android.app.job.JobWorkItem
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import android.os.AsyncTask
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.util.ArrayList
import java.util.HashMap

/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Source from
// https://github.com/aosp-mirror/platform_frameworks_support/blob/64ac15541ebbfda5e1e45a130043202658a5a809/core/src/main/java/androidx/core/app/JobIntentService.java
// Modified by OneSignal to add useWakefulService option
// This allows using ether a IntentService even for Android 8.0 (API 26) when needed.
//    - This allows starting NotificationExtenderService on a high priority FCM message
//    - Even when the device is in doze mode.
// Has fallback handling to job service if IllegalStateException is thrown when starting service.
// ==== Testing ====
// - The following can be used to confirm;
//   - adb shell dumpsys deviceidle force-idle
// - And the following to undo
//   - adb shell dumpsys deviceidle unforce
/**
 * Helper for processing work that has been enqueued for a job/service.  When running on
 * [Android O][Build.VERSION_CODES.O] or later, the work will be dispatched
 * as a job via [JobScheduler.enqueue].  When running
 * on older versions of the platform, it will use
 * [Context.startService].
 *
 *
 * You must publish your subclass in your manifest for the system to interact with.  This
 * should be published as a [android.app.job.JobService], as described for that class,
 * since on O and later platforms it will be executed that way.
 *
 *
 * Use [.enqueueWork] to enqueue new work to be
 * dispatched to and handled by your service.  It will be executed in
 * [.onHandleWork].
 *
 *
 * You do not need to use [androidx.legacy.content.WakefulBroadcastReceiver]
 * when using this class.  When running on [Android O][Build.VERSION_CODES.O],
 * the JobScheduler will take care of wake locks for you (holding a wake lock from the time
 * you enqueue work until the job has been dispatched and while it is running).  When running
 * on previous versions of the platform, this wake lock handling is emulated in the class here
 * by directly calling the PowerManager; this means the application must request the
 * [android.Manifest.permission.WAKE_LOCK] permission.
 *
 *
 * There are a few important differences in behavior when running on
 * [Android O][Build.VERSION_CODES.O] or later as a Job vs. pre-O:
 *
 *
 *  *
 *
 *When running as a pre-O service, the act of enqueueing work will generally start
 * the service immediately, regardless of whether the device is dozing or in other
 * conditions.  When running as a Job, it will be subject to standard JobScheduler
 * policies for a Job with a [JobInfo.Builder.setOverrideDeadline]
 * of 0: the job will not run while the device is dozing, it may get delayed more than
 * a service if the device is under strong memory pressure with lots of demand to run
 * jobs.
 *  *
 *
 *When running as a pre-O service, the normal service execution semantics apply:
 * the service can run indefinitely, though the longer it runs the more likely the system
 * will be to outright kill its process, and under memory pressure one should expect
 * the process to be killed even of recently started services.  When running as a Job,
 * the typical [android.app.job.JobService] execution time limit will apply, after
 * which the job will be stopped (cleanly, not by killing the process) and rescheduled
 * to continue its execution later.  Job are generally not killed when the system is
 * under memory pressure, since the number of concurrent jobs is adjusted based on the
 * memory state of the device.
 *
 *
 *
 * Here is an example implementation of this class:
 *
 * {@sample frameworks/support/samples/Support4Demos/src/main/java/com/example/android/supportv4/app/SimpleJobIntentService.java
 * *      complete}
 */
abstract class JobIntentService : Service() {
    private var mJobImpl: CompatJobEngine? = null
    private var mCompatWorkEnqueuer: WorkEnqueuer? = null
    private var mCurProcessor: CommandProcessor? = null
    private var mInterruptIfStopped = false

    /**
     * Returns true if [.onStopCurrentWork] has been called.  You can use this,
     * while executing your work, to see if it should be stopped.
     */
    private var isStopped = false
    private var mDestroyed = false
    private val mCompatQueue: ArrayList<CompatWorkItem>?

    // Class only used to create a unique hash key for sClassWorkEnqueuer
    private class ComponentNameWithWakeful internal constructor(
        private val componentName: ComponentName,
        private val useWakefulService: Boolean
    )

    /**
     * Base class for the target service we can deliver work to and the implementation of
     * how to deliver that work.
     */
    internal abstract class WorkEnqueuer(val mComponentName: ComponentName) {
        var mHasJobId = false
        var mJobId = 0
        fun ensureJobId(jobId: Int) {
            if (!mHasJobId) {
                mHasJobId = true
                mJobId = jobId
            } else require(mJobId == jobId) {
                ("Given job ID " + jobId
                        + " is different than previous " + mJobId)
            }
        }

        abstract fun enqueueWork(work: Intent)
        open fun serviceStartReceived() {}
        open fun serviceProcessingStarted() {}
        open fun serviceProcessingFinished() {}
    }

    /**
     * Get rid of lint warnings about API levels.
     */
    internal interface CompatJobEngine {
        fun compatGetBinder(): IBinder
        fun dequeueWork(): GenericWorkItem?
    }

    /**
     * An implementation of WorkEnqueuer that works for pre-O (raw Service-based).
     */
    internal class CompatWorkEnqueuer(context: Context, cn: ComponentName) : WorkEnqueuer(cn) {
        private val mContext: Context
        private val mLaunchWakeLock: PowerManager.WakeLock
        private val mRunWakeLock: PowerManager.WakeLock
        var mLaunchingService = false
        var mServiceProcessing = false
        override fun enqueueWork(work: Intent) {
            val intent = Intent(work)
            intent.component = mComponentName
            if (DEBUG) Log.d(TAG, "Starting service for work: $work")
            if (mContext.startService(intent) != null) {
                synchronized(this) {
                    if (!mLaunchingService) {
                        mLaunchingService = true
                        if (!mServiceProcessing) {
                            // If the service is not already holding the wake lock for
                            // itself, acquire it now to keep the system running until
                            // we get this work dispatched.  We use a timeout here to
                            // protect against whatever problem may cause it to not get
                            // the work.
                            mLaunchWakeLock.acquire((60 * 1000).toLong())
                        }
                    }
                }
            }
        }

        override fun serviceStartReceived() {
            synchronized(this) {
                // Once we have started processing work, we can count whatever last
                // enqueueWork() that happened as handled.
                mLaunchingService = false
            }
        }

        override fun serviceProcessingStarted() {
            synchronized(this) {
                // We hold the wake lock as long as the service is processing commands.
                if (!mServiceProcessing) {
                    mServiceProcessing = true
                    // Keep the device awake, but only for at most 10 minutes at a time
                    // (Similar to JobScheduler.)
                    mRunWakeLock.acquire(10 * 60 * 1000L)
                    mLaunchWakeLock.release()
                }
            }
        }

        override fun serviceProcessingFinished() {
            synchronized(this) {
                if (mServiceProcessing) {
                    // If we are transitioning back to a wakelock with a timeout, do the same
                    // as if we had enqueued work without the service running.
                    if (mLaunchingService) {
                        mLaunchWakeLock.acquire((60 * 1000).toLong())
                    }
                    mServiceProcessing = false
                    mRunWakeLock.release()
                }
            }
        }

        init {
            mContext = context.applicationContext
            // Make wake locks.  We need two, because the launch wake lock wants to have
            // a timeout, and the system does not do the right thing if you mix timeout and
            // non timeout (or even changing the timeout duration) in one wake lock.
            val pm = context.getSystemService(POWER_SERVICE) as PowerManager
            mLaunchWakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                cn.className + ":launch"
            )
            mLaunchWakeLock.setReferenceCounted(false)
            mRunWakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                cn.className + ":run"
            )
            mRunWakeLock.setReferenceCounted(false)
        }
    }

    /**
     * Implementation of a JobServiceEngine for interaction with JobIntentService.
     */
    @RequiresApi(26)
    internal class JobServiceEngineImpl(val mService: JobIntentService) : JobServiceEngine(
        mService
    ), CompatJobEngine {
        val mLock = Any()
        var mParams: JobParameters? = null

        internal inner class WrapperWorkItem(val mJobWork: JobWorkItem) : GenericWorkItem {
            override val intent: Intent
                get() = mJobWork.intent

            override fun complete() {
                synchronized(mLock) {
                    if (mParams != null) {
                        try {
                            mParams!!.completeWork(mJobWork)
                            // The following catches are to prevent errors completely work that
                            //    is done or hasn't started.
                            // Example:
                            // Caused by java.lang.IllegalArgumentException:
                            //     Given work is not active: JobWorkItem {
                            //       id=4 intent=Intent { (has extras) } dcount=1
                            //     }
                            // Issue: https://github.com/OneSignal/OneSignal-Android-SDK/issues/644
                        } catch (e: SecurityException) {
                            Log.e(
                                TAG,
                                "SecurityException: Failed to run mParams.completeWork(mJobWork)!",
                                e
                            )
                        } catch (e: IllegalArgumentException) {
                            Log.e(
                                TAG,
                                "IllegalArgumentException: Failed to run mParams.completeWork(mJobWork)!",
                                e
                            )
                        }
                    }
                }
            }
        }

        override fun compatGetBinder(): IBinder {
            return binder
        }

        override fun onStartJob(params: JobParameters): Boolean {
            if (DEBUG) Log.d(TAG, "onStartJob: $params")
            mParams = params
            // We can now start dequeuing work!
            mService.ensureProcessorRunningLocked(false)
            return true
        }

        override fun onStopJob(params: JobParameters): Boolean {
            if (DEBUG) Log.d(TAG, "onStopJob: $params")
            val result = mService.doStopCurrentWork()
            synchronized(mLock) {
                // Once we return, the job is stopped, so its JobParameters are no
                // longer valid and we should not be doing anything with them.
                mParams = null
            }
            return result
        }

        /**
         * Dequeue some work.
         */
        override fun dequeueWork(): GenericWorkItem? {
            var work: JobWorkItem?
            synchronized(mLock) {
                if (mParams == null) return null
                work = try {
                    mParams!!.dequeueWork()
                } catch (e: SecurityException) {
                    // Work around for https://issuetracker.google.com/issues/63622293
                    // https://github.com/OneSignal/OneSignal-Android-SDK/issues/673
                    // Caller no longer running, last stopped +###ms because: last work dequeued
                    Log.e(TAG, "Failed to run mParams.dequeueWork()!", e)
                    return null
                }
            }
            return if (work != null) {
                work!!.intent.setExtrasClassLoader(mService.classLoader)
                WrapperWorkItem(work!!)
            } else null
        }

        companion object {
            const val TAG = "JobServiceEngineImpl"
            const val DEBUG = false
        }
    }

    @RequiresApi(26)
    internal class JobWorkEnqueuer(context: Context, cn: ComponentName, jobId: Int) :
        WorkEnqueuer(cn) {
        private val mJobInfo: JobInfo
        private val mJobScheduler: JobScheduler
        override fun enqueueWork(work: Intent) {
            if (DEBUG) Log.d(TAG, "Enqueueing work: $work")
            mJobScheduler.enqueue(mJobInfo, JobWorkItem(work))
        }

        init {
            ensureJobId(jobId)
            val b = JobInfo.Builder(jobId, mComponentName)
            mJobInfo = b.setOverrideDeadline(0).build()
            mJobScheduler = context.applicationContext.getSystemService(
                JOB_SCHEDULER_SERVICE
            ) as JobScheduler
        }
    }

    /**
     * Abstract definition of an item of work that is being dispatched.
     */
    internal interface GenericWorkItem {
        val intent: Intent
        fun complete()
    }

    /**
     * An implementation of GenericWorkItem that dispatches work for pre-O platforms: intents
     * received through a raw service's onStartCommand.
     */
    internal inner class CompatWorkItem(override val intent: Intent, val mStartId: Int) :
        GenericWorkItem {
        override fun complete() {
            if (DEBUG) Log.d(TAG, "Stopping self: #$mStartId")
            stopSelf(mStartId)
        }
    }

    /**
     * This is a task to dequeue and process work in the background.
     */
    internal inner class CommandProcessor : AsyncTask<Void?, Void?, Void?>() {
        override fun doInBackground(vararg p0: Void?): Void? {
            var work: GenericWorkItem
            if (DEBUG) Log.d(TAG, "Starting to dequeue work...")
            while (dequeueWork().also { work = it!! } != null) {
                if (DEBUG) Log.d(TAG, "Processing next work: $work")
                onHandleWork(work.intent)
                if (DEBUG) Log.d(TAG, "Completing work: $work")
                work.complete()
            }
            if (DEBUG) Log.d(TAG, "Done processing work!")
            return null
        }

        override fun onCancelled(aVoid: Void?) {
            processorFinished()
        }

        override fun onPostExecute(aVoid: Void?) {
            processorFinished()
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (DEBUG) Log.d(TAG, "CREATING: $this")
        if (Build.VERSION.SDK_INT >= 26) {
            mJobImpl = JobServiceEngineImpl(this)
            mCompatWorkEnqueuer = null
        }
        val cn = ComponentName(this, this.javaClass)
        mCompatWorkEnqueuer = getWorkEnqueuer(this, cn, false, 0, true)
    }

    /**
     * Processes start commands when running as a pre-O service, enqueueing them to be
     * later dispatched in [.onHandleWork].
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mCompatWorkEnqueuer!!.serviceStartReceived()
        if (DEBUG) Log.d(TAG, "Received compat start command #$startId: $intent")
        synchronized(mCompatQueue!!) {
            mCompatQueue.add(
                CompatWorkItem(
                    intent ?: Intent(),
                    startId
                )
            )
            ensureProcessorRunningLocked(true)
        }
        return START_REDELIVER_INTENT
    }

    /**
     * Returns the IBinder for the [JobServiceEngine] when
     * running as a JobService on O and later platforms.
     */
    override fun onBind(intent: Intent): IBinder? {
        return if (mJobImpl != null) {
            val engine = mJobImpl!!.compatGetBinder()
            if (DEBUG) Log.d(
                TAG,
                "Returning engine: $engine"
            )
            engine
        } else {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        doStopCurrentWork()
        synchronized(mCompatQueue!!) {
            mDestroyed = true
            mCompatWorkEnqueuer!!.serviceProcessingFinished()
        }
    }

    /**
     * Called serially for each work dispatched to and processed by the service.  This
     * method is called on a background thread, so you can do long blocking operations
     * here.  Upon returning, that work will be considered complete and either the next
     * pending work dispatched here or the overall service destroyed now that it has
     * nothing else to do.
     *
     *
     * Be aware that when running as a job, you are limited by the maximum job execution
     * time and any single or total sequential items of work that exceeds that limit will
     * cause the service to be stopped while in progress and later restarted with the
     * last unfinished work.  (There is currently no limit on execution duration when
     * running as a pre-O plain Service.)
     *
     * @param intent The intent describing the work to now be processed.
     */
    protected abstract fun onHandleWork(intent: Intent)

    /**
     * Control whether code executing in [.onHandleWork] will be interrupted
     * if the job is stopped.  By default this is false.  If called and set to true, any
     * time [.onStopCurrentWork] is called, the class will first call
     * [AsyncTask.cancel(true)][AsyncTask.cancel] to interrupt the running
     * task.
     *
     * @param interruptIfStopped Set to true to allow the system to interrupt actively
     * running work.
     */
    fun setInterruptIfStopped(interruptIfStopped: Boolean) {
        mInterruptIfStopped = interruptIfStopped
    }

    /**
     * This will be called if the JobScheduler has decided to stop this job.  The job for
     * this service does not have any constraints specified, so this will only generally happen
     * if the service exceeds the job's maximum execution time.
     *
     * @return True to indicate to the JobManager whether you'd like to reschedule this work,
     * false to drop this and all following work. Regardless of the value returned, your service
     * must stop executing or the system will ultimately kill it.  The default implementation
     * returns true, and that is most likely what you want to return as well (so no work gets
     * lost).
     */
    fun onStopCurrentWork(): Boolean {
        return true
    }

    fun doStopCurrentWork(): Boolean {
        if (mCurProcessor != null) {
            mCurProcessor!!.cancel(mInterruptIfStopped)
        }
        isStopped = true
        return onStopCurrentWork()
    }

    fun ensureProcessorRunningLocked(reportStarted: Boolean) {
        if (mCurProcessor == null) {
            mCurProcessor = CommandProcessor()
            if (mCompatWorkEnqueuer != null && reportStarted) {
                mCompatWorkEnqueuer!!.serviceProcessingStarted()
            }
            if (DEBUG) Log.d(TAG, "Starting processor: $mCurProcessor")
            mCurProcessor!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }
    }

    fun processorFinished() {
        if (mCompatQueue != null) {
            synchronized(mCompatQueue) {
                mCurProcessor = null
                // The async task has finished, but we may have gotten more work scheduled in the
                // meantime.  If so, we need to restart the new processor to execute it.  If there
                // is no more work at this point, either the service is in the process of being
                // destroyed (because we called stopSelf on the last intent started for it), or
                // someone has already called startService with a new Intent that will be
                // arriving shortly.  In either case, we want to just leave the service
                // waiting -- either to get destroyed, or get a new onStartCommand() callback
                // which will then kick off a new processor.
                if (mCompatQueue != null && mCompatQueue.size > 0) {
                    ensureProcessorRunningLocked(false)
                } else if (!mDestroyed) {
                    mCompatWorkEnqueuer!!.serviceProcessingFinished()
                }
            }
        }
    }

    internal fun dequeueWork(): GenericWorkItem? {
        if (mJobImpl != null) {
            val jobWork = mJobImpl!!.dequeueWork()
            if (jobWork != null) return jobWork
        }
        synchronized(mCompatQueue!!) { return if (mCompatQueue.size > 0) mCompatQueue.removeAt(0) else null }
    }

    companion object {
        const val TAG = "JobIntentService"
        const val DEBUG = false
        val sLock = Any()
        private val sClassWorkEnqueuer = HashMap<ComponentNameWithWakeful, WorkEnqueuer>()

        /**
         * Call this to enqueue work for your subclass of [JobIntentService].  This will
         * either directly start the service (when running on pre-O platforms) or enqueue work
         * for it as a job (when running on O and later).  In either case, a wake lock will be
         * held for you to ensure you continue running.  The work you enqueue will ultimately
         * appear at [.onHandleWork].
         *
         * @param context Context this is being called from.
         * @param cls The concrete class the work should be dispatched to (this is the class that
         * is published in your manifest).
         * @param jobId A unique job ID for scheduling; must be the same value for all work
         * enqueued for the same class.
         * @param work The Intent of work to enqueue.
         */
        fun enqueueWork(
            context: Context, cls: Class<*>, jobId: Int,
            work: Intent, useWakefulService: Boolean
        ) {
            enqueueWork(context, ComponentName(context, cls), jobId, work, useWakefulService)
        }

        /**
         * Like [.enqueueWork], but supplies a ComponentName
         * for the service to interact with instead of its class.
         *
         * @param context Context this is being called from.
         * @param component The published ComponentName of the class this work should be
         * dispatched to.
         * @param jobId A unique job ID for scheduling; must be the same value for all work
         * enqueued for the same class.
         * @param work The Intent of work to enqueue.
         */
        fun enqueueWork(
            context: Context, component: ComponentName,
            jobId: Int, work: Intent, useWakefulService: Boolean
        ) {
            requireNotNull(work) { "work must not be null" }
            synchronized(sLock) {
                var we = getWorkEnqueuer(context, component, true, jobId, useWakefulService)
                we.ensureJobId(jobId)

                // Can throw on API 26+ if useWakefulService=true and app is NOT whitelisted.
                // One example is when an FCM high priority message is received the system will
                // temporarily whitelist the app. However it is possible that it does not end up getting
                //    whitelisted so we need to catch this and fall back to a job service.
                try {
                    we.enqueueWork(work)
                } catch (e: IllegalStateException) {
                    if (useWakefulService) {
                        we = getWorkEnqueuer(context, component, true, jobId, false)
                        we.enqueueWork(work)
                    } else throw e
                }
            }
        }

        internal fun getWorkEnqueuer(
            context: Context, cn: ComponentName, hasJobId: Boolean,
            jobId: Int, useWakefulService: Boolean
        ): WorkEnqueuer {
            val key = ComponentNameWithWakeful(cn, useWakefulService)
            var we = sClassWorkEnqueuer[key]
            if (we == null) {
                we = if (Build.VERSION.SDK_INT >= 26 && !useWakefulService) {
                    require(hasJobId) { "Can't be here without a job id" }
                    JobWorkEnqueuer(context, cn, jobId)
                } else CompatWorkEnqueuer(context, cn)
                sClassWorkEnqueuer[key] = we
            }
            return we
        }
    }

    /**
     * Default empty constructor.
     */
    init {
        mCompatQueue = ArrayList()
    }
}