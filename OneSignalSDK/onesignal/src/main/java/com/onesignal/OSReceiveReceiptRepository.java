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

import org.json.JSONException;
import org.json.JSONObject;

class OSReceiveReceiptRepository {

    private static final String APP_ID = "app_id";
    private static final String PLAYER_ID = "player_id";

    void sendReceiveReceipt(@NonNull String appId, @NonNull String playerId, @NonNull String notificationId, @NonNull OneSignalRestClient.ResponseHandler responseHandler) {
        try {
            JSONObject jsonBody = new JSONObject()
                    .put(APP_ID, appId)
                    .put(PLAYER_ID, playerId);

            OneSignalRestClient.put("notifications/" + notificationId + "/report_received", jsonBody, responseHandler);
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating direct receive receipt:JSON Failed.", e);
        }
    }
}
