package com.onesignal.notification.internal.receivereceipt.impl

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.onesignal.core.OneSignal
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.common.AndroidUtils
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.params.IParamsService
import com.onesignal.notification.internal.receivereceipt.IReceiveReceiptProcessor
import com.onesignal.notification.internal.receivereceipt.IReceiveReceiptWorkManager
import java.util.concurrent.TimeUnit

internal class ReceiveReceiptWorkManager(
    private val _paramsService: IParamsService,
    private val _applicationService: IApplicationService
) : IReceiveReceiptWorkManager {

    private val minDelay = 0
    private val maxDelay = 25

    override fun enqueueReceiveReceipt(notificationId: String) {
        if (!_paramsService.receiveReceiptEnabled) {
            Logging.debug("sendReceiveReceipt disabled")
            return
        }
        val delay: Int = AndroidUtils.getRandomDelay(minDelay, maxDelay)
        val inputData = Data.Builder()
            .putString(OS_NOTIFICATION_ID, notificationId)
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
            val notificationId = inputData.getString(OS_NOTIFICATION_ID)
            _receiveReceiptProcessor.sendReceiveReceipt(notificationId!!)
            return Result.success()
        }
    }

    companion object {
        private const val OS_NOTIFICATION_ID = "os_notification_id"
    }
}
