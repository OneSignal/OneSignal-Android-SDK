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

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl

object OSWorkManagerHelper {
    /**
     * Helper method to provide a way to check if WorkManager is initialized in this process.
     *
     * This is effectively the `WorkManager.isInitialized()` public method introduced in androidx.work:work-*:2.8.0-alpha02.
     * Please see https://android-review.googlesource.com/c/platform/frameworks/support/+/1941186.
     *
     * @return `true` if WorkManager has been initialized in this process.
     */
    @SuppressWarnings("deprecation")
    @SuppressLint("RestrictedApi")
    private fun isInitialized(): Boolean {
        return WorkManagerImpl.getInstance() != null
    }

    /**
     * If there is an instance of WorkManager available, use it. Else, in rare cases, initialize it ourselves.
     *
     * Calling `WorkManager.getInstance(context)` directly can cause an exception if it is null.
     *
     * @return an instance of WorkManager
     */
    @JvmStatic
    @Synchronized
    fun getInstance(context: Context): WorkManager {
        if (!isInitialized()) {
            WorkManager.initialize(context, Configuration.Builder().build())
        }
        return WorkManager.getInstance(context)
    }
}
