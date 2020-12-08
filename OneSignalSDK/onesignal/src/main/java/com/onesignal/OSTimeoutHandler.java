/**
 * Modified MIT License
 * <p>
 * Copyright 2020 OneSignal
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

import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;

class OSTimeoutHandler extends HandlerThread {

    private static final String TAG = OSTimeoutHandler.class.getCanonicalName();
    private static final Object SYNC_LOCK = new Object();

    private static OSTimeoutHandler timeoutHandler;

    static OSTimeoutHandler getTimeoutHandler() {
        if (timeoutHandler == null) {
            synchronized (SYNC_LOCK) {
                if (timeoutHandler == null)
                    timeoutHandler = new OSTimeoutHandler();
            }
        }
        return timeoutHandler;
    }

    private final Handler mHandler;

    private OSTimeoutHandler() {
        super(TAG);
        start();
        this.mHandler = new Handler(getLooper());
    }

    void startTimeout(long timeout, @NonNull Runnable runnable) {
        synchronized (SYNC_LOCK) {
            // Avoid duplicate runnable postDelayed
            destroyTimeout(runnable);
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Running startTimeout with timeout: " + timeout + " and runnable: " + runnable.toString());
            mHandler.postDelayed(runnable, timeout);
        }
    }

    void destroyTimeout(Runnable runnable) {
        synchronized (SYNC_LOCK) {
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Running destroyTimeout with runnable: " + runnable.toString());
            mHandler.removeCallbacks(runnable);
        }
    }
}
