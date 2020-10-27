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

import android.content.Context;

class OSFocusDelaySync extends OSBackgroundSync {

    private static final Object INSTANCE_LOCK = new Object();
    private static final String SYNC_TASK_THREAD_ID = "OS_FOCUS_SYNCSRV_BG_SYNC";
    private static final int SYNC_TASK_ID = 2081862118;
    // We want to perform a on_focus sync as soon as the session is done to report the time
    private static final long SYNC_AFTER_BG_DELAY_MS = 2000;

    private static OSFocusDelaySync sInstance;

    static OSFocusDelaySync getInstance() {
        if (sInstance == null) {
            synchronized (INSTANCE_LOCK) {
                if (sInstance == null)
                    sInstance = new OSFocusDelaySync();
            }
        }
        return sInstance;
    }

    @Override
    protected String getSyncTaskThreadId() {
        return SYNC_TASK_THREAD_ID;
    }

    @Override
    protected int getSyncTaskId() {
        return SYNC_TASK_ID;
    }

    @Override
    protected Class getSyncServiceJobClass() {
        return FocusDelaySyncJobService.class;
    }

    @Override
    protected Class getSyncServicePendingIntentClass() {
        return FocusDelaySyncService.class;
    }

    @Override
    protected void scheduleSyncTask(Context context) {
        OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "OSFocusDelaySync scheduleSyncTask:SYNC_AFTER_BG_DELAY_MS: " + SYNC_AFTER_BG_DELAY_MS);
        scheduleBackgroundSyncTask(context, SYNC_AFTER_BG_DELAY_MS);
    }

}
