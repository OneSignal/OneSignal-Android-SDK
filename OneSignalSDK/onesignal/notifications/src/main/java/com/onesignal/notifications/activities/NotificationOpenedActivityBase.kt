/**
 * Modified MIT License
 *
 * Copyright 2021 OneSignal
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
package com.onesignal.notifications.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.onesignal.OneSignal
import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.notifications.internal.open.INotificationOpenedProcessor

abstract class NotificationOpenedActivityBase : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        processIntent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processIntent()
    }

    internal open fun processIntent() {
        suspendifyOnThread {
            if (!OneSignal.initWithContext(applicationContext)) {
                return@suspendifyOnThread
            }

            val openedProcessor = OneSignal.getService<INotificationOpenedProcessor>()

            openedProcessor.processFromContext(this, intent)

            // KEEP: Xiaomi Compatibility:
            // Must keep this Activity alive while trampolining, that is
            // startActivity() must be called BEFORE finish(), otherwise
            // the app is never foregrounded.
            finish()
        }
    }
}
