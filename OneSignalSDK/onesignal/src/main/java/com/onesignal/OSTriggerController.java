package com.onesignal;

import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class OSTriggerController {
    private final ConcurrentHashMap<String, Object> triggers;

    private static final String TRIGGERS_KEY = "os_triggers";

    OSTriggerController() {
        triggers = new ConcurrentHashMap<>();
    }

    /**
     * This function evaluates all of the triggers for a message. The triggers are organized in
     * a 2D array where the outer array represents OR conditions and the inner array represents
     * AND conditions. If all of the triggers in an inner array evaluate to true, it means the
     * message should be shown and the function returns true.
    */
    boolean evaluateMessageTriggers(OSInAppMessage message) {

        // the outer loop represents OR conditions
        for (ArrayList<OSTrigger> conditions : message.triggers) {

            // only evaluate dynamic triggers after we've evaluated all others
            ArrayList<OSTrigger> dynamicTriggers = new ArrayList<>();

            // will be set to false if any non-time-based trigger evaluates to false
            // This way we don't bother scheduling timers if the message can't be shown anyways
            boolean continueToEvaluateDynamicTriggers = true;

            // the inner loop represents AND conditions
            for (int i = 0; i < conditions.size(); i++) {
                OSTrigger trigger = conditions.get(i);

                // If it's a dynamic trigger, these are only processed if all other
                // triggers for this message evaluate to true, so add it to the array
                if (OSDynamicTriggerType.fromString(trigger.property) != null) {
                    dynamicTriggers.add(trigger);
                    continue;
                } else if (triggers.get(trigger.property) == null) {

                    // if we don't have a local value for this trigger, it can only
                    // ever evaluate to true if using the NOT_EXISTS operator
                    if (trigger.operatorType == OSTrigger.OSTriggerOperatorType.NOT_EXISTS ||
                        (trigger.operatorType == OSTrigger.OSTriggerOperatorType.NOT_EQUAL_TO && trigger.value != null)) {
                        continue;
                    }

                    continueToEvaluateDynamicTriggers = false;
                    break;
                } else if (trigger.operatorType == OSTrigger.OSTriggerOperatorType.EXISTS) {
                    continue;
                } else if (trigger.operatorType == OSTrigger.OSTriggerOperatorType.NOT_EXISTS) {
                    // the trigger value exists if we reach this point, so it's false and we should break
                    continueToEvaluateDynamicTriggers = false;
                    break;
                }

                Object realValue = triggers.get(trigger.property);

                // CONTAINS operates on arrays, we check every element of the array
                // to see if it contains the trigger value.
                if (trigger.operatorType == OSTrigger.OSTriggerOperatorType.CONTAINS) {
                    if (!(realValue instanceof ArrayList) || !((Collection)realValue).contains(trigger.value)) {
                        continueToEvaluateDynamicTriggers = false;
                        break;
                    }
                    // We are only checking to see if the trigger is FALSE. If a trigger evaluates
                    // to false, we immediately break the inner AND loop to proceed to the next OR loop
                } else if (realValue instanceof String && trigger.value instanceof String && !triggerMatchesStringValue((String)trigger.value, (String)realValue, trigger.operatorType)) {
                    continueToEvaluateDynamicTriggers = false;
                    break;
                    // In Java, we cannot use instanceof since we want to be able to compare floats to ints and so on
                    // Checking Double instanceof Number appears to return false, so we must use isAssignableTo()
                } else if (trigger.value instanceof Number && realValue instanceof Number
                            && !triggerMatchesNumericValue((Number)trigger.value, (Number)realValue, trigger.operatorType)) {
                    continueToEvaluateDynamicTriggers = false;
                    break;
                } else if (!(trigger.value instanceof Number) && trigger.value != null && trigger.value.getClass() != realValue.getClass()) {
                    continueToEvaluateDynamicTriggers = false;
                    break;
                }
            }

            if (continueToEvaluateDynamicTriggers && dynamicTriggers.size() == 0)
                return true;
            else if (!continueToEvaluateDynamicTriggers)
                break;

            // TODO: Check dynamic triggers
        }

        return false;
    }

    private boolean triggerMatchesStringValue(String triggerValue, String savedValue, OSTrigger.OSTriggerOperatorType operator) {
        switch (operator) {
            case EQUAL_TO:
                return triggerValue.equals(savedValue);
            case NOT_EQUAL_TO:
                return !triggerValue.equals(savedValue);
            default:
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, "Attempted to use an invalid operator for a string trigger comparison: " + operator.toString());
                return false;
        }
    }

    private boolean triggerMatchesNumericValue(Number triggerValue, Number savedValue, OSTrigger.OSTriggerOperatorType operator) {
        switch (operator) {
            case EXISTS:
            case CONTAINS:
            case NOT_EXISTS:
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, "Attempted to use an invalid operator on numeric values: " + operator.toString());
                return false;
            case EQUAL_TO:
                return savedValue.equals(triggerValue);
            case LESS_THAN:
                return savedValue.doubleValue() < triggerValue.doubleValue();
            case GREATER_THAN:
                return savedValue.doubleValue() > triggerValue.doubleValue();
            case NOT_EQUAL_TO:
                return !savedValue.equals(triggerValue);
            case LESS_THAN_OR_EQUAL_TO:
                return savedValue.doubleValue() < triggerValue.doubleValue() || savedValue.equals(triggerValue);
            case GREATER_THAN_OR_EQUAL_TO:
                return savedValue.doubleValue() > triggerValue.doubleValue() || savedValue.equals(triggerValue);
            default:
                return false;
        }
    }

    public enum OSDynamicTriggerType {
        SESSION_DURATION("os_session_duration"),
        TIME("os_time");

        private String text;

        OSDynamicTriggerType(String type) {
            this.text = type;
        }

        @Override
        public String toString() {
            return this.text;
        }

        public static OSDynamicTriggerType fromString(String text) {
            for (OSDynamicTriggerType type : OSDynamicTriggerType.values()) {
                if (type.text.equalsIgnoreCase(text)) {
                    return type;
                }
            }

            return null;
        }
    }

    /** Trigger Set/Delete/Persist Logic */
    void addTriggers(Map<String, Object> newTriggers) {
        synchronized (triggers) {
            for (String key : newTriggers.keySet()) {
                Object value = newTriggers.get(key);

                triggers.put(key, value);
            }

            /*
                TODO: Refactor so that we don't have to re-write all trigger key/value
                pairs to disk every single time we save a trigger
            */
            OneSignalPrefs.saveObject(OneSignalPrefs.PREFS_TRIGGERS, TRIGGERS_KEY, triggers);
        }
    }

    void removeTriggersForKeys(Collection<String> keys) {
        synchronized (triggers) {
            for (String key : keys) {
                triggers.remove(key);
            }

            OneSignalPrefs.saveObject(OneSignalPrefs.PREFS_TRIGGERS, TRIGGERS_KEY, triggers);
        }
    }

    @Nullable
    Object getTriggerValue(String key) {
        synchronized (triggers) {
            if (triggers.containsKey(key))
                return triggers.get(key);
            else
                return null;
        }
    }
}