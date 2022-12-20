package com.onesignal.inAppMessages.internal.triggers.impl

import com.onesignal.debug.internal.logging.Logging
import java.util.Timer
import java.util.TimerTask

/**
 * A static wrapper around [Timer] to schedule a task to run in the future
 *
 * Note: Due to issues with testing the Java utility Timer class, we've created a wrapper class
 *       that schedules the timer.
 */
internal object DynamicTriggerTimer {
    fun scheduleTrigger(task: TimerTask?, triggerId: String, delay: Long) {
        Logging.debug("scheduleTrigger: $triggerId delay: $delay")
        val timer = Timer("trigger_timer:$triggerId")
        timer.schedule(task, delay)
    }
}
