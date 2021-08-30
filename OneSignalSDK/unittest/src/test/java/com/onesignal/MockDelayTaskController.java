package com.onesignal;

import androidx.annotation.NonNull;

public class MockDelayTaskController extends OSDelayTaskController {
    private int mockedRandomValue = 0;
    private boolean runOnSameThread = true;

    public MockDelayTaskController(OSLogger logger) {
        super(logger);
    }

    protected int getRandomDelay() {
        return mockedRandomValue;
    }

    public void delayTaskByRandom(@NonNull Runnable runnable) {
        if (runOnSameThread) {
            runnable.run();
        } else {
            super.delayTaskByRandom(runnable);
        }
    }

    public void setMockedRandomValue(int mockedRandomValue) {
        this.mockedRandomValue = mockedRandomValue;
    }

    public void setRunOnSameThread(boolean runOnSameThread) {
        this.runOnSameThread = runOnSameThread;
    }
}
