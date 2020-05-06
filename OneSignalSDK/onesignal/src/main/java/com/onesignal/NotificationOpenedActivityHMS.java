/**
 * Modified MIT License
 *
 * Copyright 2020 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.onesignal;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// HMS Core creates a notification with an Intent when opened to start this Activity.
//   Intent is defined via OneSignal's backend and is sent to HMS.
// This has to be it's own Activity separate from NotificationOpenedActivity since
//   we do not have full control over the Bundle.
// Designed to be started with these flags
// Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET | Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_NEW_TASK
// This way if app developer does not want the app to launch then it won't do so

public class NotificationOpenedActivityHMS extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        processIntent();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        processIntent();
    }

    private void processIntent() {
        processOpen(getIntent());
        finish();
    }

    private void processOpen(@Nullable Intent intent) {
        // Validate Intent to prevent any side effects or crashes
        //    if triggered outside of OneSignal for any reason.
        if (!OSNotificationFormatHelper.isOneSignalIntent(intent))
            return;
        OneSignal.setAppContext(this);

        Bundle bundle = intent.getExtras();
        JSONObject jsonData = NotificationBundleProcessor.bundleAsJSONObject(bundle);

        if (NotificationOpenedProcessor.handleIAMPreviewOpen(this, jsonData))
            return;

        OneSignal.handleNotificationOpen(
            this,
            new JSONArray().put(jsonData),
            false,
            OSNotificationFormatHelper.getOSNotificationIdFromJson(jsonData)
        );
    }

}