/**
 * Modified MIT License
 * <p>
 * Copyright 2023 OneSignal
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

package com.onesignal

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager

object OSWorkManagerHelper {
    /**
     * If there is an instance of WorkManager available, use it. Else, in rare cases, initialize it ourselves.
     *
     * Calling `WorkManager.getInstance(context)` directly can cause an exception if it is null.
     * However, this is the appropriate way to check as the non-throwing `WorkManager.getInstance()`
     * method does not allow the opportunity for on-demand initialization with custom WorkManager.
     *
     * The purpose of this helper is to work around this bug - https://issuetracker.google.com/issues/258176803
     *
     * @return an instance of WorkManager
     */
    @JvmStatic
    @Synchronized
    fun getInstance(context: Context): WorkManager {
        return try {
            WorkManager.getInstance(context)
        } catch (e: IllegalStateException) {
            /*
            This aims to catch the IllegalStateException "WorkManager is not initialized properly..." -
            https://androidx.tech/artifacts/work/work-runtime/2.8.1-source/androidx/work/impl/WorkManagerImpl.java.html
             */
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "OSWorkManagerHelper.getInstance failed, attempting to initialize: ", e)
            initializeWorkManager(context)
            WorkManager.getInstance(context)
        }
    }

    /**
     * This method is called in rare cases to initialize WorkManager ourselves.
     */
    private fun initializeWorkManager(context: Context) {
        try {
            /*
            Note: `WorkManager.getInstance(context)` should already initialize WorkManager when the
            application implements Configuration.Provider. However, as a further safeguard, let's detect
            for this when we attempt to initialize WorkManager.
             */
            val configuration = (context.applicationContext as? Configuration.Provider)?.workManagerConfiguration ?: Configuration.Builder().build()
            WorkManager.initialize(context, configuration)
        } catch (e: IllegalStateException) {
            /*
            This catch is meant for the exception -
            https://android.googlesource.com/platform/frameworks/support/+/60ae0eec2a32396c22ad92502cde952c80d514a0/work/workmanager/src/main/java/androidx/work/impl/WorkManagerImpl.java#177
            1. We lost the race with another call to  WorkManager.initialize outside of OneSignal.
            2. It is possible for some other unexpected error is thrown from WorkManager.
             */
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "OSWorkManagerHelper initializing WorkManager failed: ", e)
        }
    }
}
