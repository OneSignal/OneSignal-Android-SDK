/**
 * Modified MIT License
 * <p>
 * Copyright 2019 OneSignal
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.onesignal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

class OSReceiveReceiptController {

    private static final String OS_NOTIFICATION_ID = "os_notification_id";
    private int minDelay = 0;
    private int maxDelay = 25;
    
    private final OSRemoteParamController remoteParamController;
    private static OSReceiveReceiptController sInstance;

    private OSReceiveReceiptController() {
        this.remoteParamController = OneSignal.getRemoteParamController();
    }

    synchronized public static OSReceiveReceiptController getInstance() {
        if (sInstance == null)
            sInstance = new OSReceiveReceiptController();
        return sInstance;
    }

    void beginEnqueueingWork(Context context, String osNotificationId) {
        if (!remoteParamController.isReceiveReceiptEnabled()) {
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "sendReceiveReceipt disabled");
            return;
        }

        int delay = OSUtils.getRandomDelay(minDelay, maxDelay);

        Data inputData = new Data.Builder()
                .putString(OS_NOTIFICATION_ID, osNotificationId)
                .build();

        Constraints constraints = buildConstraints();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ReceiveReceiptWorker.class)
                .setConstraints(constraints)
                .setInitialDelay(delay, TimeUnit.SECONDS)
                .setInputData(inputData)
                .build();

        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "OSReceiveReceiptController enqueueing send receive receipt work with notificationId: " + osNotificationId + " and delay: " + delay + " seconds");

        WorkManager.getInstance(context)
                .enqueueUniqueWork(osNotificationId + "_receive_receipt", ExistingWorkPolicy.KEEP, workRequest);
    }

    Constraints buildConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }

    public static class ReceiveReceiptWorker extends Worker {

        public ReceiveReceiptWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            Data inputData = getInputData();
            String notificationId = inputData.getString(OS_NOTIFICATION_ID);

            sendReceiveReceipt(notificationId);

            return Result.success();
        }

        void sendReceiveReceipt(@NonNull final String notificationId) {
            final String appId = OneSignal.appId == null || OneSignal.appId.isEmpty() ? OneSignal.getSavedAppId() : OneSignal.appId;
            final String playerId = OneSignal.getUserId();
            Integer deviceType = null;

            OSReceiveReceiptRepository repository = new OSReceiveReceiptRepository();

            try {
                deviceType = new OSUtils().getDeviceType();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }

            final Integer finalDeviceType = deviceType;
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "ReceiveReceiptWorker: Device Type is: " + finalDeviceType);

            repository.sendReceiveReceipt(appId, playerId, finalDeviceType, notificationId, new OneSignalRestClient.ResponseHandler() {
                @Override
                void onSuccess(String response) {
                    OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Receive receipt sent for notificationID: " + notificationId);
                }

                @Override
                void onFailure(int statusCode, String response, Throwable throwable) {
                    OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Receive receipt failed with statusCode: " + statusCode + " response: " + response);
                }
            });
        }
    }
}
