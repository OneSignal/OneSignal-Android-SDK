package com.onesignal;

import java.util.ArrayList;
import java.util.Date;
import java.util.TimerTask;

import com.onesignal.OSTrigger.OSTriggerOperator;

class OSDynamicTriggerController {

    interface OSDynamicTriggerControllerObserver {
        // Alerts the observer that a trigger timer has fired
        void messageTriggerConditionChanged();
    }

    private final OSDynamicTriggerControllerObserver observer;

    private static final double REQUIRED_ACCURACY = 0.3;
    // Assume last time an In-App Message was displayed a very very long time ago.
    private static final long DEFAULT_LAST_IN_APP_TIME_AGO = 999_999;

    private final ArrayList<String> scheduledMessages;

    static Date sessionLaunchTime = new Date();

    OSDynamicTriggerController(OSDynamicTriggerControllerObserver triggerObserver) {
        scheduledMessages = new ArrayList<>();
        observer = triggerObserver;
    }

    boolean dynamicTriggerShouldFire(final OSTrigger trigger) {
        if (trigger.value == null)
            return false;

        synchronized (scheduledMessages) {
            // All time-based trigger values should be numbers (either timestamps or offsets)
            if (!(trigger.value instanceof Number))
                return false;

            long currentTimeInterval = 0;
            switch (trigger.kind) {
                case SESSION_TIME:
                    currentTimeInterval = new Date().getTime() - sessionLaunchTime.getTime();
                    break;
                case TIME_SINCE_LAST_IN_APP:
                    if (OSInAppMessageController.getController().isDisplayingInApp())
                        return false;
                    Date lastTimeAppDismissed = OSInAppMessageController.getController().lastTimeInAppDismissed;
                    if (lastTimeAppDismissed == null)
                        currentTimeInterval = DEFAULT_LAST_IN_APP_TIME_AGO;
                    else
                        currentTimeInterval = new Date().getTime() - lastTimeAppDismissed.getTime();
                    break;
            }

            long requiredTimeInterval = (long) (((Number) trigger.value).doubleValue() * 1_000);
            if (evaluateTimeIntervalWithOperator(requiredTimeInterval, currentTimeInterval, trigger.operatorType))
                return true;

            long offset = requiredTimeInterval - currentTimeInterval;
            if (offset <= 0L)
                return false;

            // Prevents re-scheduling timers for messages that we're already waiting on
            if (scheduledMessages.contains(trigger.triggerId))
                return false;

            OSDynamicTriggerTimer.scheduleTrigger(new TimerTask() {
                @Override
                public void run() {
                    scheduledMessages.remove(trigger.triggerId);
                    observer.messageTriggerConditionChanged();
                }
            }, trigger.triggerId, offset);

            scheduledMessages.add(trigger.triggerId);
        }

        return false;
    }

    private static boolean evaluateTimeIntervalWithOperator(double timeInterval, double currentTimeInterval, OSTriggerOperator operator) {
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

    private static boolean roughlyEqual(double left, double right) {
        return Math.abs(left - right) < REQUIRED_ACCURACY;
    }
}
