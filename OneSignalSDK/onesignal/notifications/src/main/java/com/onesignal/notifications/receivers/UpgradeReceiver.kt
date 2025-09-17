/**
 * Modified MIT License
 *
 * Copyright 2016 OneSignal
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
package com.onesignal.notifications.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.onesignal.OneSignal
import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.notifications.internal.restoration.INotificationRestoreWorkManager

class UpgradeReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        // TODO: Now that we arent restoring like we use to, think we can remove this? Ill do some
        //  testing and look at the issue but maybe someone has a answer or rems what directly
        //  was causing this issue
        // Return early if using Android 7.0 due to upgrade restore crash (#263)
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.N) {
            return
        }

        suspendifyOnThread {
            if (!OneSignal.initWithContext(context.applicationContext)) {
                return@suspendifyOnThread
            }

            val restoreWorkManager = OneSignal.getService<INotificationRestoreWorkManager>()
            restoreWorkManager.beginEnqueueingWork(context, true)
        }
    }
}
