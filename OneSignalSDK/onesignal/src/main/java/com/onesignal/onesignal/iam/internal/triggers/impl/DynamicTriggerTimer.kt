package com.onesignal.onesignal.iam.internal.triggers.impl

import com.onesignal.onesignal.core.internal.logging.Logging
import java.util.*

// Due to issues with testing the Java utility Timer class, we've created a wrapper class
// that schedules the timer.
internal object DynamicTriggerTimer {
    fun scheduleTrigger(task: TimerTask?, triggerId: String, delay: Long) {
        Logging.debug("scheduleTrigger: $triggerId delay: $delay")
        val timer = Timer("trigger_timer:$triggerId")
        timer.schedule(task, delay)
    }
}