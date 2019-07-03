package com.onesignal;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

@Implements(OSDynamicTriggerTimer.class)
public class ShadowDynamicTimer {

    /** Allows you to control if trigger timers actually get scheduled */
    public static boolean shouldScheduleTimers = true;

    /** Allows us to simply check if a timer was scheduled at all */
    public static boolean hasScheduledTimer = false;

    /** The delay value for the most recently scheduled timer */
    private static long mostRecentlyScheduledTimerDelay = 0;

    // Timers are recorded and force stopped after test to ensure they don't carry over.
    private static ArrayList<Timer> timers = new ArrayList<>();
    private static ArrayList<TimerTask> timerTasks = new ArrayList<>();

    public static void resetStatics() {
        cancelTimers();
        shouldScheduleTimers = true;
        hasScheduledTimer = false;
        mostRecentlyScheduledTimerDelay = 0;
    }
    private static void cancelTimers() {
        for (Timer timer : timers)
            timer.cancel();
        timers = new ArrayList<>();

        for(TimerTask timerTask : timerTasks)
            timerTask.cancel();
        timerTasks = new ArrayList<>();
    }

    /** Allows us to see when the OSDynamicTriggerController schedules a timer */
    @Implementation
    public static void scheduleTrigger(TimerTask task, String triggerId, long delay) {
        mostRecentlyScheduledTimerDelay = delay;

        if (shouldScheduleTimers) {
            hasScheduledTimer = true;
            Timer timer = new Timer("trigger_test:" + triggerId);
            timer.schedule(task, delay);
            timers.add(timer);
            timerTasks.add(task);
        }
    }

    public static double mostRecentTimerDelaySeconds() {
        return (double)mostRecentlyScheduledTimerDelay / 1000.0f;
    }
}
