package com.onesignal;

import android.util.Log;

import com.onesignal.OSTrigger.OSTriggerOperatorType;
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import com.onesignal.OSTriggerController.OSDynamicTriggerType;

class OSDynamicTriggerController {
    private static final double REQUIRED_ACCURACY = 0.3;

    private ArrayList<String> scheduledMessages;

    private static Date sessionLaunchTime = new Date();

    OSDynamicTriggerController() {
        scheduledMessages = new ArrayList<>();
    }

    boolean dynamicTriggerShouldFire(OSTrigger trigger, String messageId) {
        if (trigger.value == null)
            return false;

        synchronized (scheduledMessages) {

            // All time-based trigger values should be numbers (either timestamps or offsets)
            if (!(trigger.value instanceof Number))
                return false;

            // prevents us from re-evaluating triggers we've already evaluated
            if (scheduledMessages.contains(trigger.triggerId))
                return false;

            double requiredTimeInterval = ((Number)trigger.value).doubleValue();

            double offset = 0.0f;

            if (OSDynamicTriggerType.SESSION_DURATION.toString().equals(trigger.property)) {

                double currentDuration = ((new Date()).getTime() - sessionLaunchTime.getTime()) / 1000.0f;

                if (evaluateTimeIntervalWithOperator(requiredTimeInterval, currentDuration, trigger.operatorType))
                    return true;

                offset = requiredTimeInterval - currentDuration;
            } else if (OSDynamicTriggerType.TIME.toString().equals(trigger.property)) {
                double currentTimestamp = (new Date()).getTime() / 1000.0f;

                if (evaluateTimeIntervalWithOperator(requiredTimeInterval, currentTimestamp, trigger.operatorType))
                    return true;

                offset = requiredTimeInterval - currentTimestamp;
            }

            if (offset <= 0.0f)
                return false;

            Timer timer = new Timer("trigger_" + trigger.triggerId);

            timer.schedule(new TimerTask() {
                @Override
                public void run() {

                }
            }, (long)(offset * 1000.0f));

            scheduledMessages.add(trigger.triggerId);
        }

        return false;
    }

    boolean evaluateTimeIntervalWithOperator(double timeInterval, double currentTimeInterval, OSTriggerOperatorType operator) {
        switch (operator) {
            case LESS_THAN:
                return currentTimeInterval < timeInterval;
            case LESS_THAN_OR_EQUAL_TO:
                return currentTimeInterval <= timeInterval || roughlyEqual(timeInterval, currentTimeInterval);
            case GREATER_THAN:
                return currentTimeInterval > timeInterval;
            case GREATER_THAN_OR_EQUAL_TO:
                return currentTimeInterval >= timeInterval || roughlyEqual(timeInterval, currentTimeInterval);
            case EQUAL_TO:
                return roughlyEqual(timeInterval, currentTimeInterval);
            case NOT_EQUAL_TO:
                return !roughlyEqual(timeInterval, currentTimeInterval);
            default:
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, "Attempted to apply an invalid operator on a time-based in-app-message trigger: " + operator.toString());
                return false;
        }
    }

    boolean roughlyEqual(double left, double right) {
        return Math.abs(left - right) < REQUIRED_ACCURACY;
    }
}
