package com.onesignal;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;

import com.huawei.agconnect.config.AGConnectServicesConfig;
import com.huawei.hms.aaid.HmsInstanceId;
import com.huawei.hms.common.ApiException;
import com.huawei.hms.push.HmsMessaging;
import com.huawei.hms.support.api.entity.core.CommonCode;

import static com.onesignal.OneSignal.LOG_LEVEL;

class PushRegistratorHMS implements PushRegistrator {

    static final String HMS_CLIENT_APP_ID = "client/app_id";

    private static final int NEW_TOKEN_TIMEOUT_MS = 30_000;

    private static boolean callbackSuccessful;
    private @Nullable static RegisteredHandler registeredHandler;

    static void fireCallback(String token) {
        if (registeredHandler == null)
            return;
        callbackSuccessful = true;
        registeredHandler.complete(token, UserState.PUSH_STATUS_SUBSCRIBED);
    }

    @Override
    public void registerForPush(@NonNull final Context context, final String senderId, @NonNull final RegisteredHandler callback) {
        registeredHandler = callback;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    getHMSTokenTask(context, callback);
                } catch (ApiException e) {
                    OneSignal.Log(LOG_LEVEL.ERROR, "HMS ApiException getting Huawei push token!", e);

                    int pushStatus;
                    if (e.getStatusCode() == CommonCode.ErrorCode.ARGUMENTS_INVALID)
                        pushStatus = UserState.PUSH_STATUS_HMS_ARGUMENTS_INVALID;
                    else
                        pushStatus = UserState.PUSH_STATUS_HMS_API_EXCEPTION_OTHER;

                    callback.complete(null, pushStatus);
                }
            }
        }, "OS_HMS_GET_TOKEN").start();
    }

    private synchronized void getHMSTokenTask(@NonNull Context context, @NonNull RegisteredHandler callback) throws ApiException {
        // Check required to prevent AGConnectServicesConfig or HmsInstanceId used below
        //   from throwing a ClassNotFoundException
        if (!OSUtils.hasAllHMSLibrariesForPushKit()) {
            callback.complete(null, UserState.PUSH_STATUS_MISSING_HMS_PUSHKIT_LIBRARY);
            return;
        }

        String appId = AGConnectServicesConfig.fromContext(context).getString(HMS_CLIENT_APP_ID);
        HmsInstanceId hmsInstanceId = HmsInstanceId.getInstance(context);

        String pushToken = hmsInstanceId.getToken(appId, HmsMessaging.DEFAULT_TOKEN_SCOPE);

        if (!TextUtils.isEmpty(pushToken)) {
            OneSignal.Log(LOG_LEVEL.INFO, "Device registered for HMS, push token = " + pushToken);
            callback.complete(pushToken, UserState.PUSH_STATUS_SUBSCRIBED);
        } else {
            waitForOnNewPushTokenEvent(callback);
        }
    }

    private static void doTimeOutWait() {
        try {
            Thread.sleep(NEW_TOKEN_TIMEOUT_MS);
        } catch (InterruptedException e) {
        }
    }

    // If EMUI 9.x or older getToken will always return null.
    // We must wait for HmsMessageService.onNewToken to fire instead.
    private void waitForOnNewPushTokenEvent(@NonNull RegisteredHandler callback) {
        doTimeOutWait();
        if (!callbackSuccessful) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "HmsMessageServiceOneSignal.onNewToken timed out.");
            callback.complete(null, UserState.PUSH_STATUS_HMS_TOKEN_TIMEOUT);
        }
    }
}
