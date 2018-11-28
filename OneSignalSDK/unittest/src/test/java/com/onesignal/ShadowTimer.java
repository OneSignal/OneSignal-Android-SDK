package com.onesignal;

import android.util.Log;

import org.robolectric.annotation.Implements;

import java.util.Timer;
import java.util.TimerTask;

@Implements(value = Timer.class, looseSignatures = true)
public class ShadowTimer extends Timer {
    public static boolean shouldScheduleTimers = true;
    public static long mostRecentlyScheduledTimerDelay = 0;

    @Override
    public void schedule(TimerTask task, long delay) {
        mostRecentlyScheduledTimerDelay = delay;

        Log.d("onesignal", "Scheduling shadow timer for delay: " + String.valueOf(delay));

        if (shouldScheduleTimers) {
            super.schedule(task, delay);
        }
    }
}
