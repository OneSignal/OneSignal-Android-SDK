package com.onesignal;

import android.os.SystemClock;

public class MockOSTime implements OSTime {

    private Long mockedTime = null;
    private Long mockedElapsedTime = null;
    private Long mockedCurrentThreadTimeMillis = null;

    public void reset() {
        mockedTime = null;
        mockedElapsedTime = null;
        mockedCurrentThreadTimeMillis = null;
    }

    @Override
    public long getCurrentTimeMillis() {
        return mockedTime != null ? mockedTime : System.currentTimeMillis();
    }

    @Override
    public long getElapsedRealtime() {
        return mockedElapsedTime != null ? mockedElapsedTime : SystemClock.elapsedRealtime();
    }

    @Override
    public long getCurrentThreadTimeMillis() {
        return mockedCurrentThreadTimeMillis != null ? mockedCurrentThreadTimeMillis : SystemClock.currentThreadTimeMillis();
    }

    public void setMockedTime(Long mockedTime) {
        this.mockedTime = mockedTime;
    }

    public void setMockedElapsedTime(Long mockedForegroundTime) {
        this.mockedElapsedTime = mockedForegroundTime;
    }

    public void setMockedCurrentThreadTimeMillis(Long mockedCurrentThreadTimeMillis) {
        this.mockedCurrentThreadTimeMillis = mockedCurrentThreadTimeMillis;
    }
}
