package com.onesignal.notifications.internal.restoration.impl

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
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
    override fun beginEnqueueingWork(
        context: Context,
        shouldDelay: Boolean,
    ) {
        enqueueWork(context, shouldDelay)
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
            return when {
                processor.process() -> Result.success()
                runAttemptCount + 1 >= MAX_RESTORE_ATTEMPTS -> Result.failure()
                else -> Result.retry()
            }
        }
    }

    companion object {
        private val NOTIFICATION_RESTORE_WORKER_IDENTIFIER =
            NotificationRestoreWorker::class.java.canonicalName ?: NotificationRestoreWorker::class.java.name
        private const val DELAYED_RESTORE_SECONDS = 15L
        internal const val MAX_RESTORE_ATTEMPTS = 3
        private val restored = AtomicBoolean(false)

        /** Returns null when restore work was already enqueued by this process. */
        @Suppress("TooGenericExceptionCaught")
        internal fun enqueueWork(
            context: Context,
            shouldDelay: Boolean,
        ): Operation? {
            if (!restored.compareAndSet(false, true)) return null

            return try {
                // Boot and upgrade delay restore so the app is not doing too much work at once.
                val restoreDelayInSeconds = if (shouldDelay) DELAYED_RESTORE_SECONDS else 0L
                val workRequest =
                    OneTimeWorkRequest.Builder(NotificationRestoreWorker::class.java)
                        .setInitialDelay(restoreDelayInSeconds, TimeUnit.SECONDS)
                        .build()
                OSWorkManagerHelper.getInstance(context)
                    .enqueueUniqueWork(
                        NOTIFICATION_RESTORE_WORKER_IDENTIFIER,
                        ExistingWorkPolicy.KEEP,
                        workRequest,
                    )
            } catch (e: Exception) {
                onEnqueueFailed()
                throw e
            }
        }

        /** Lets a later caller retry after WorkManager rejected the enqueue. */
        internal fun onEnqueueFailed() {
            restored.set(false)
        }

        internal fun resetForTest() {
            restored.set(false)
        }
    }
}
