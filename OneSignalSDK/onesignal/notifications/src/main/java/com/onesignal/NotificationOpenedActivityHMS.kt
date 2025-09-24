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
package com.onesignal
// OneSignal backend includes the activity name in the payload, modifying the namespace may result in notification click not firing

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.notifications.internal.open.INotificationOpenedProcessorHMS

// HMS Core creates a notification with an Intent when opened to start this Activity.
//   Intent is defined via OneSignal's backend and is sent to HMS.
// This has to be it's own Activity separate from NotificationOpenedActivity since
//   we do not have full control over the Bundle.
// Designed to be started with these flags:
// Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
//   - This gives us the hex value of 0x58000000
// This is to meet the following requirements
//   1. Allows the app developer to omit foregrounding the app if they choose
//      - FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK is required to do this
//      - FLAG_ACTIVITY_SINGLE_TOP nor FLAG_ACTIVITY_NEW_TASK on it's own does this.
//      - FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET would also work but this is deprecated.
//   2. This Activity does not become part of the back stack.
//      - FLAG_ACTIVITY_NO_HISTORY does this
//   3. This Activity does not show in the recent Activities list.
//      - FLAG_ACTIVITY_NO_HISTORY does this
// Note on using  Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
//   We always want to create a new task so it doesn't resume some existing task.
//   If it resumes an existing task then it won't be possible to prevent the app from showing.
//   Some developer want to process the click in the background without bring the app to the foreground.
//   The launcher Activity is started in OneSignal.java:startOrResumeApp if the developer does want the app
//     to be opened.
class NotificationOpenedActivityHMS : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        processIntent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processIntent()
    }

    private fun processIntent() {
        processOpen(intent)
        finish()
    }

    private fun processOpen(intent: Intent?) {
        // run init in background
        suspendifyOnThread {
            if (!OneSignal.initWithContext(applicationContext)) {
                return@suspendifyOnThread
            }

            val notificationPayloadProcessorHMS = OneSignal.getService<INotificationOpenedProcessorHMS>()
            val self = this
            notificationPayloadProcessorHMS.handleHMSNotificationOpenIntent(self, intent)
        }
    }
}
