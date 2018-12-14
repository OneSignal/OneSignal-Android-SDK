package com.onesignal;

import org.robolectric.annotation.Implements;

import java.util.Timer;
import java.util.TimerTask;

@Implements(OSDynamicTriggerTimer.class)
public class ShadowDynamicTimer {

    /** Allows you to control if trigger timers actually get scheduled */
    public static boolean shouldScheduleTimers = true;

    /** Allows us to simply check if a timer was scheduled at all */
    public static boolean hasScheduledTimer = false;

    /** The delay value for the most recently scheduled timer */
    public static long mostRecentlyScheduledTimerDelay = 0;

    /** Allows us to see when the OSDynamicTriggerController schedules a timer */
    public static void scheduleTrigger(TimerTask task, String triggerId, long delay) {
        mostRecentlyScheduledTimerDelay = delay;

        if (shouldScheduleTimers) {
            hasScheduledTimer = true;

            Timer timer = new Timer("trigger_test:" + triggerId);

            timer.schedule(task, delay);
        }
    }

    public static double mostRecentTimerDelaySeconds() {
        return (double)mostRecentlyScheduledTimerDelay / 1000.0f;
    }
}
