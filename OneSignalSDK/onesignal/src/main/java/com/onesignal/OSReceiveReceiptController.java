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

import android.support.annotation.NonNull;

class OSReceiveReceiptController {

    private final OSReceiveReceiptRepository repository;

    private static OSReceiveReceiptController sInstance;
    private OSReceiveReceiptController() {
        this.repository = new OSReceiveReceiptRepository();
    }
    public static synchronized OSReceiveReceiptController getInstance() {
        if (sInstance == null)
            sInstance = new OSReceiveReceiptController();
        return sInstance;
    }

    void sendReceiveReceipt(@NonNull final String notificationId) {
        String appId = OneSignal.appId == null || OneSignal.appId.isEmpty() ? OneSignal.getSavedAppId() : OneSignal.appId;
        String playerId = OneSignal.getUserId();

        if (!isReceiveReceiptEnabled()) {
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "sendReceiveReceipt disable");
            return;
        }

        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "sendReceiveReceipt appId: " + appId + " playerId: " + playerId + " notificationId: " + notificationId);
        repository.sendReceiveReceipt(appId, playerId, notificationId, new OneSignalRestClient.ResponseHandler() {
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

    private boolean isReceiveReceiptEnabled() {
        return OneSignalPrefs.getBool(
           OneSignalPrefs.PREFS_ONESIGNAL,
           OneSignalPrefs.PREFS_OS_RECEIVE_RECEIPTS_ENABLED,
           false
        );
    }
}
