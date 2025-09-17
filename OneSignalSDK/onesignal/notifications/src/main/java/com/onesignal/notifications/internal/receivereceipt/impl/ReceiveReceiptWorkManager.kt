package com.onesignal.notifications.internal.receivereceipt.impl

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkerParameters
import com.onesignal.OneSignal
import com.onesignal.OneSignal.ensureOneSignalInitialized
import com.onesignal.common.AndroidUtils
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.common.OSWorkManagerHelper
import com.onesignal.notifications.internal.receivereceipt.IReceiveReceiptProcessor
import com.onesignal.notifications.internal.receivereceipt.IReceiveReceiptWorkManager
import com.onesignal.user.internal.subscriptions.ISubscriptionManager
import java.util.concurrent.TimeUnit

internal class ReceiveReceiptWorkManager(
    private val _applicationService: IApplicationService,
    private val _configModelStore: ConfigModelStore,
    private val _subscriptionManager: ISubscriptionManager,
) : IReceiveReceiptWorkManager {
    private val minDelay = 0
    private val maxDelay = 25

    override fun enqueueReceiveReceipt(notificationId: String) {
        if (!_configModelStore.model.receiveReceiptEnabled) {
            Logging.debug("sendReceiveReceipt disabled")
            return
        }

        val appId: String = _configModelStore.model.appId
        val subscriptionId = _subscriptionManager.subscriptions.push.id

        if (subscriptionId.isEmpty() || appId.isEmpty()) {
            Logging.debug("ReceiveReceiptWorkManager: No push subscription or appId!")
        }

        val delay: Int = AndroidUtils.getRandomDelay(minDelay, maxDelay)
        val inputData =
            Data.Builder()
                .putString(OS_NOTIFICATION_ID, notificationId)
                .putString(OS_APP_ID, appId)
                .putString(OS_SUBSCRIPTION_ID, subscriptionId)
                .build()
        val constraints = buildConstraints()
        val workRequest =
            OneTimeWorkRequest.Builder(ReceiveReceiptWorker::class.java)
                .setConstraints(constraints)
                .setInitialDelay(delay.toLong(), TimeUnit.SECONDS)
                .setInputData(inputData)
                .build()
        Logging.debug(
            "OSReceiveReceiptController enqueueing send receive receipt work with notificationId: $notificationId and delay: $delay seconds",
        )
        OSWorkManagerHelper.getInstance(_applicationService.appContext)
            .enqueueUniqueWork(
                notificationId + "_receive_receipt",
                ExistingWorkPolicy.KEEP,
                workRequest,
            )
    }

    private fun buildConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    class ReceiveReceiptWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            if (!ensureOneSignalInitialized(applicationContext)) {
                Logging.warn("ReceiveReceiptWorker skipped due to failed OneSignal initialization")
                return Result.success()
            }

            val notificationId = inputData.getString(OS_NOTIFICATION_ID) ?: return Result.failure()
            val appId = inputData.getString(OS_APP_ID) ?: return Result.failure()
            val subscriptionId = inputData.getString(OS_SUBSCRIPTION_ID) ?: return Result.failure()

            val receiveReceiptProcessor = OneSignal.getService<IReceiveReceiptProcessor>()
            receiveReceiptProcessor.sendReceiveReceipt(appId, subscriptionId, notificationId)
            return Result.success()
        }
    }

    companion object {
        private const val OS_NOTIFICATION_ID = "os_notification_id"
        private const val OS_APP_ID = "os_app_id"
        private const val OS_SUBSCRIPTION_ID = "os_subscription_id"
    }
}
