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
import java.util.concurrent.atomic.AtomicBoolean

internal class NotificationRestoreWorkManager : INotificationRestoreWorkManager {
    @Suppress("TooGenericExceptionCaught")
    override fun beginEnqueueingWork(
        context: Context,
        shouldDelay: Boolean,
    ) {
        if (!restored.compareAndSet(false, true)) return

        try {
            val restoreDelayInSeconds = if (shouldDelay) 15 else 0
            val workRequest =
                OneTimeWorkRequest.Builder(NotificationRestoreWorker::class.java)
                    .setInitialDelay(restoreDelayInSeconds.toLong(), TimeUnit.SECONDS)
                    .build()
            OSWorkManagerHelper.getInstance(context)
                .enqueueUniqueWork(
                    NOTIFICATION_RESTORE_WORKER_IDENTIFIER,
                    ExistingWorkPolicy.KEEP,
                    workRequest,
                )
        } catch (e: Exception) {
            restored.set(false)
            throw e
        }
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
        private val NOTIFICATION_RESTORE_WORKER_IDENTIFIER =
            NotificationRestoreWorker::class.java.canonicalName ?: NotificationRestoreWorker::class.java.name
        private val restored = AtomicBoolean(false)
    }
}
