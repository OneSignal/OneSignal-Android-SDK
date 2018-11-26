package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.onesignal.OSTrigger.OSTriggerOperatorType;
import com.onesignal.OneSignalPrefs;

class OSTriggerController {
    private ConcurrentHashMap<String, Object> triggers;

    private static OSTriggerController sharedInstance;

    private static final String TRIGGERS_KEY = "os_triggers";

    private OSTriggerController() {
        this.triggers = new ConcurrentHashMap<>();
    }

    static OSTriggerController getController() {
        if (sharedInstance == null) {
            sharedInstance = new OSTriggerController();
        }

        return sharedInstance;
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

            // the inner loop represents AND conditions
            for (int i = 0; i < conditions.size(); i++) {
                OSTrigger trigger = conditions.get(i);

                // If the current trigger evaluates to true and we are
                // at the last element, the entire function returns true
                // since every trigger in the AND block is true
                boolean lastElement = i == conditions.size() - 1;

                // If it's a dynamic trigger, these are only processed if all other
                // triggers for this message evaluate to true, so add it to the array
                if (OSDynamicTriggerType.fromString(trigger.property) != null) {
                    dynamicTriggers.add(trigger);
                    continue;
                } else if (this.triggers.get(trigger.property) == null) {

                    // if we don't have a local value for this trigger, it can only
                    // ever evaluate to true if using the NOT_EXISTS operator
                    if (trigger.operatorType == OSTriggerOperatorType.NOT_EXISTS) {
                        if (lastElement) {
                            return true;
                        } else continue;
                    }

                    break;
                } else if (trigger.operatorType == OSTriggerOperatorType.EXISTS) {
                    if (lastElement) {
                        return true;
                    } else continue;
                } else if (trigger.operatorType == OSTriggerOperatorType.NOT_EXISTS) {
                    // the trigger value exists if we reach this point, so it's false and we should break
                    break;
                }

                Object realValue = this.triggers.get(trigger.property);

                // CONTAINS operates on arrays, we check every element of the array
                // to see if it contains the trigger value.
                if (trigger.operatorType == OSTriggerOperatorType.CONTAINS) {
                    if (!(realValue instanceof ArrayList) || !((ArrayList)realValue).contains(trigger.value)) {
                        break;
                    } else if (lastElement) {
                        return true;
                    } else continue;

                    // We are only checking to see if the trigger is FALSE. If a trigger evaluates
                    // to false, we immediately break the inner AND loop to proceed to the next OR loop
                } else if (realValue.getClass() != trigger.value.getClass() ||
                        (realValue instanceof  Number && !triggerMatchesNumericValue((Number)trigger.value, (Number)realValue, trigger.operatorType)) ||
                        (realValue instanceof String && !triggerMatchesStringValue((String)trigger.value, (String)realValue, trigger.operatorType))) {
                    break;
                } else if (lastElement) {
                    // if we reach this point, it means the current trigger (and
                    // all previous triggers in the AND block) evaluated to true.
                    return true;
                }
            }
        }

        return false;
    }

    private boolean triggerMatchesStringValue(String triggerValue, String savedValue, OSTriggerOperatorType operator) {
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

    private boolean triggerMatchesNumericValue(Number triggerValue, Number savedValue, OSTriggerOperatorType operator) {
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
    void addTriggers(Map<String, Object> triggers) {
        synchronized (this.triggers) {
            for (String key : triggers.keySet()) {
                Object value = triggers.get(key);

                this.triggers.put(key, value);
            }

            /*
                TODO: Refactor so that we don't have to re-write all trigger key/value
                pairs to disk every single time we save a trigger
            */
            OneSignalPrefs.saveObject(OneSignalPrefs.PREFS_TRIGGERS, TRIGGERS_KEY, this.triggers);
        }
    }

    void removeTriggersForKeys(Collection<String> keys) {
        synchronized (this.triggers) {
            for (String key : keys) {
                this.triggers.remove(key);
            }

            OneSignalPrefs.saveObject(OneSignalPrefs.PREFS_TRIGGERS, TRIGGERS_KEY, this.triggers);
        }
    }

    @Nullable
    Object getTriggerValue(String key) {
        synchronized (this.triggers) {
            if (this.triggers.containsKey(key))
                return this.triggers.get(key);
            else
                return null;
        }
    }
}