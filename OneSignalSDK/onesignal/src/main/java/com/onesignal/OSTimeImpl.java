package com.onesignal;

import android.os.SystemClock;

public class OSTimeImpl implements OSTime {
    @Override
    public long getCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public long getElapsedRealtime() {
        return SystemClock.elapsedRealtime();
    }
}
