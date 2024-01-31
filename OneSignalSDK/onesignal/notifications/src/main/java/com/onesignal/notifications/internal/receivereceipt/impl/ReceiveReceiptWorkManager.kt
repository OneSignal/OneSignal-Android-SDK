package com.onesignal.notifications.internal.receivereceipt.impl

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.onesignal.OneSignal
import com.onesignal.common.AndroidUtils
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.receivereceipt.IReceiveReceiptProcessor
import com.onesignal.notifications.internal.receivereceipt.IReceiveReceiptWorkManager
import com.onesignal.user.internal.subscriptions.ISubscriptionManager
import java.util.concurrent.TimeUnit

internal class ReceiveReceiptWorkManager(
    private val _applicationService: IApplicationService,
    private val _configModelStore: ConfigModelStore,
    private val _subscriptionManager: ISubscriptionManager
) : IReceiveReceiptWorkManager {

    private val minDelay = 0
    private val maxDelay = 25

    override fun enqueueReceiveReceipt(notificationId: String) {
        if (!_configModelStore.model.receiveReceiptEnabled) {
            Logging.debug("sendReceiveReceipt disabled")
            return
        }

        val appId: String = _configModelStore.model.appId
        val subscriptionId: String? = _subscriptionManager.subscriptions.push?.id

        if (subscriptionId == null || appId.isEmpty()) {
            Logging.debug("ReceiveReceiptWorkManager: No push subscription or appId!")
        }

        val delay: Int = AndroidUtils.getRandomDelay(minDelay, maxDelay)
        val inputData = Data.Builder()
            .putString(OS_NOTIFICATION_ID, notificationId)
            .putString(OS_APP_ID, appId)
            .putString(OS_SUBSCRIPTION_ID, subscriptionId)
            .build()
        val constraints = buildConstraints()
        val workRequest = OneTimeWorkRequest.Builder(ReceiveReceiptWorker::class.java)
            .setConstraints(constraints)
            .setInitialDelay(delay.toLong(), TimeUnit.SECONDS)
            .setInputData(inputData)
            .build()
        Logging.debug("OSReceiveReceiptController enqueueing send receive receipt work with notificationId: $notificationId and delay: $delay seconds")
        WorkManager.getInstance(_applicationService.appContext)
            .enqueueUniqueWork(
                notificationId + "_receive_receipt",
                ExistingWorkPolicy.KEEP,
                workRequest
            )
    }

    private fun buildConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    class ReceiveReceiptWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
        private var _receiveReceiptProcessor: IReceiveReceiptProcessor = OneSignal.getService()

        override suspend fun doWork(): Result {
            val inputData = inputData
            val notificationId = inputData.getString(OS_NOTIFICATION_ID)!!
            val appId = inputData.getString(OS_APP_ID)!!
            val subscriptionId = inputData.getString(OS_SUBSCRIPTION_ID)!!
            _receiveReceiptProcessor.sendReceiveReceipt(appId, subscriptionId, notificationId)
            return Result.success()
        }
    }

    companion object {
        private const val OS_NOTIFICATION_ID = "os_notification_id"
        private const val OS_APP_ID = "os_app_id"
        private const val OS_SUBSCRIPTION_ID = "os_subscription_id"
    }
}
