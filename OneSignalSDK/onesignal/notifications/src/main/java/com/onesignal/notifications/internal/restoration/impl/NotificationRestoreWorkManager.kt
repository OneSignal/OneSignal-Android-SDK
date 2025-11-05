package com.onesignal.notifications.internal.restoration.impl

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkerParameters
import com.onesignal.OneSignal
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.common.NotificationHelper
import com.onesignal.notifications.internal.common.OSWorkManagerHelper
import com.onesignal.notifications.internal.restoration.INotificationRestoreProcessor
import com.onesignal.notifications.internal.restoration.INotificationRestoreWorkManager
import java.util.concurrent.TimeUnit

internal class NotificationRestoreWorkManager : INotificationRestoreWorkManager {
    // Notifications will never be force removed when the app's process is running,
    //   so we only need to restore at most once per cold start of the app.
    private var restored = false
    private val lock = Any()

    override fun beginEnqueueingWork(
        context: Context,
        shouldDelay: Boolean,
    ) {
        // Only allow one piece of work to be enqueued.
        synchronized(lock) {
            if (restored) {
                return
            }

            restored = true
        }

        // When boot or upgrade, add a 15 second delay to alleviate app doing to much work all at once
        val restoreDelayInSeconds = if (shouldDelay) 15 else 0
        val workRequest =
            OneTimeWorkRequest.Builder(NotificationRestoreWorker::class.java)
                .setInitialDelay(restoreDelayInSeconds.toLong(), TimeUnit.SECONDS)
                .build()
        OSWorkManagerHelper.getInstance(context!!)
            .enqueueUniqueWork(
                NOTIFICATION_RESTORE_WORKER_IDENTIFIER,
                ExistingWorkPolicy.KEEP,
                workRequest,
            )
    }

    class NotificationRestoreWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            val context = applicationContext

            val initialized = OneSignal.initWithContext(context)
            if (!initialized) {
                Logging.warn("NotificationRestoreWorker skipped due to failed OneSignal init")
                return Result.success()
            }

            if (!NotificationHelper.areNotificationsEnabled(context)) {
                Logging.debug("NotificationRestoreWorker failed: Notifications disabled")
                return Result.failure()
            }

            val processor = OneSignal.getService<INotificationRestoreProcessor>()
            processor.process()

            return Result.success()
        }
    }

    companion object {
        private val NOTIFICATION_RESTORE_WORKER_IDENTIFIER = NotificationRestoreWorker::class.java.canonicalName
    }
}
