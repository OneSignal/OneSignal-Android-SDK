package com.onesignal;

import org.jetbrains.annotations.NotNull;

public class MockDelayTaskController extends OSDelayTaskController {
    private int mockedRandomValue = 0;
    private boolean runOnSameThread = true;

    public MockDelayTaskController(OSLogger logger) {
        super(logger);
    }

    protected int getRandomNumber() {
        return mockedRandomValue;
    }

    public void delayTaskByRandom(@NotNull Runnable runnable) {
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
