package com.onesignal;

import java.util.Timer;
import java.util.TimerTask;

// Due to issues with testing the Java utility Timer class, we've created a wrapper class
// that schedules the timer.
class OSDynamicTriggerTimer {
    static void scheduleTrigger(TimerTask task, String triggerId, long delay) {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "scheduleTrigger: " + triggerId + " delay: " + delay);
        Timer timer = new Timer("trigger_timer:" + triggerId);
        timer.schedule(task, delay);
    }
}