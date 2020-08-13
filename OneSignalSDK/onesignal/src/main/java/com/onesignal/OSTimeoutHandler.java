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

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

class OSTimeoutHandler {

    private long timeout;

    private Handler timeoutHandler;
    private Runnable timeoutRunnable;

    void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    synchronized void startTimeout(@NonNull final Runnable runnable) {
        // If the handler or runnable isn't null we do not want to start another
        if (timeoutHandler != null && timeoutRunnable != null)
            return;

        timeoutRunnable = runnable;

        if (Looper.myLooper() == null)
            Looper.prepare();

        timeoutHandler = new Handler();
        timeoutHandler.postDelayed(timeoutRunnable, timeout);

        // TODO: This line causes some UnitTests to run forever and not sure how to fix
        //  This is required though for apps that have a no complete inside of the NotificationProcessing or NotificationWillShowInForeground handlers
        Looper.loop();
    }

    synchronized void destroyTimeout() {
        if (timeoutHandler != null)
            timeoutHandler.removeCallbacks(timeoutRunnable);

        timeoutHandler = null;
        timeoutRunnable = null;
    }

}
