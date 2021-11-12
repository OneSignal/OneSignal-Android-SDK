package com.onesignal;

public class MockOSTimeImpl extends OSTimeImpl {

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
        return mockedTime != null ? mockedTime : super.getCurrentTimeMillis();
    }

    @Override
    public long getElapsedRealtime() {
        return mockedElapsedTime != null ? mockedElapsedTime : super.getElapsedRealtime();
    }

    @Override
    public long getCurrentThreadTimeMillis() {
        return mockedCurrentThreadTimeMillis != null ? mockedCurrentThreadTimeMillis : super.getCurrentThreadTimeMillis();
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

    public void advanceSystemTimeBy(long sec) {
        long ms = sec * 1_000L;
        setMockedTime(getCurrentTimeMillis() + ms);
    }

    public void advanceThreadTimeBy(long sec) {
        long ms = sec * 1_000L;
        setMockedCurrentThreadTimeMillis(getCurrentThreadTimeMillis() + ms);
    }

    public void advanceSystemAndElapsedTimeBy(long sec) {
        long ms = sec * 1_000L;
        setMockedElapsedTime(getCurrentTimeMillis() + ms);
        advanceSystemTimeBy(sec);
    }

}
