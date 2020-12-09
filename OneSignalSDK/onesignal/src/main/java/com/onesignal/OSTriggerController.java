package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.onesignal.OSDynamicTriggerController.OSDynamicTriggerControllerObserver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.onesignal.OSTrigger.OSTriggerOperator;

class OSTriggerController {

    OSDynamicTriggerController dynamicTriggerController;

    private final ConcurrentHashMap<String, Object> triggers;

    OSTriggerController(OSDynamicTriggerControllerObserver dynamicTriggerObserver) {
        triggers = new ConcurrentHashMap<>();
        dynamicTriggerController = new OSDynamicTriggerController(dynamicTriggerObserver);
    }

    /**
     * This function evaluates all of the triggers for a message. The triggers are organized in
     * a 2D array where the outer array represents OR conditions and the inner array represents
     * AND conditions. If all of the triggers in an inner array evaluate to true, it means the
     * message should be shown and the function returns true.
     */
    boolean evaluateMessageTriggers(@NonNull OSInAppMessage message) {
        // If there are no triggers then we display the In-App when a new session is triggered
        if (message.triggers.size() == 0)
            return true;

        // Outer loop represents OR conditions
        for (ArrayList<OSTrigger> andConditions : message.triggers) {
            if (evaluateAndTriggers(andConditions))
                return true;
        }

        return false;
    }

    private boolean evaluateAndTriggers(@NonNull ArrayList<OSTrigger> andConditions) {
        for (OSTrigger trigger : andConditions) {
            if (!this.evaluateTrigger(trigger))
                return false;
        }
        return true;
    }

    private boolean evaluateTrigger(@NonNull OSTrigger trigger) {
        // Assume all unknown trigger kinds to be false to be safe.
        if (trigger.kind == OSTrigger.OSTriggerKind.UNKNOWN)
            return false;

        if (trigger.kind != OSTrigger.OSTriggerKind.CUSTOM)
            return dynamicTriggerController.dynamicTriggerShouldFire(trigger);

        final OSTriggerOperator operatorType = trigger.operatorType;
        final Object deviceValue = triggers.get(trigger.property);

        if (deviceValue == null) {
            // If we don't have a local value for this trigger, can only be true in two cases;
            // 1. If operator is Not Exists
            if (operatorType == OSTriggerOperator.NOT_EXISTS)
                return true;

            // 2. Checking to make sure the key doesn't equal a specific value, other than null of course.
            return operatorType == OSTriggerOperator.NOT_EQUAL_TO && trigger.value != null;
        }

        // We have local value at this point, we can evaluate existence checks
        if (operatorType == OSTriggerOperator.EXISTS)
            return true;
        if (operatorType == OSTriggerOperator.NOT_EXISTS)
            return false;

        if (operatorType == OSTriggerOperator.CONTAINS)
            return deviceValue instanceof Collection && ((Collection) deviceValue).contains(trigger.value);

        if (deviceValue instanceof String &&
                trigger.value instanceof String &&
                triggerMatchesStringValue((String) trigger.value, (String) deviceValue, operatorType))
            return true;

        if (trigger.value instanceof Number &&
                deviceValue instanceof Number &&
                triggerMatchesNumericValue((Number) trigger.value, (Number) deviceValue, operatorType))
            return true;

        if (triggerMatchesFlex(trigger.value, deviceValue, operatorType))
            return true;

        // No matches, evaluate to false
        return false;
    }

    private boolean triggerMatchesStringValue(@NonNull String triggerValue, @NonNull String deviceValue, @NonNull OSTriggerOperator operator) {
        switch (operator) {
            case EQUAL_TO:
                return triggerValue.equals(deviceValue);
            case NOT_EQUAL_TO:
                return !triggerValue.equals(deviceValue);
            default:
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, "Attempted to use an invalid operator for a string trigger comparison: " + operator.toString());
                return false;
        }
    }

    // Allow converting of deviceValues to other types to allow triggers to be more forgiving.
    private boolean triggerMatchesFlex(@Nullable Object triggerValue, @NonNull Object deviceValue, @NonNull OSTriggerOperator operator) {
        if (triggerValue == null)
            return false;

        // If operator is equal or not equals ignore type by comparing on toString values
        if (operator.checksEquality())
            return triggerMatchesStringValue(triggerValue.toString(), deviceValue.toString(), operator);

        if (deviceValue instanceof String &&
            triggerValue instanceof Number)
            return triggerMatchesNumericValueFlex((Number) triggerValue, (String) deviceValue, operator);
        return false;
    }

    private boolean triggerMatchesNumericValueFlex(@NonNull Number triggerValue, @NonNull String deviceValue, @NonNull OSTriggerOperator operator) {
        double deviceDoubleValue;
        try {
            deviceDoubleValue = Double.parseDouble(deviceValue);
        } catch (NumberFormatException e) {
            return false;
        }

        return triggerMatchesNumericValue(triggerValue.doubleValue(), deviceDoubleValue, operator);
    }

    private boolean triggerMatchesNumericValue(@NonNull Number triggerValue, @NonNull Number deviceValue, @NonNull OSTriggerOperator operator) {
        double triggerDoubleValue = triggerValue.doubleValue();
        double deviceDoubleValue = deviceValue.doubleValue();

        switch (operator) {
            case EXISTS:
            case CONTAINS:
            case NOT_EXISTS:
                OneSignal.onesignalLog(
                   OneSignal.LOG_LEVEL.ERROR,
                   "Attempted to use an invalid operator with a numeric value: " + operator.toString()
                );
                return false;
            case EQUAL_TO:
                return deviceDoubleValue == triggerDoubleValue;
            case NOT_EQUAL_TO:
                return deviceDoubleValue != triggerDoubleValue;
            case LESS_THAN:
                return deviceDoubleValue < triggerDoubleValue;
            case GREATER_THAN:
                return deviceDoubleValue > triggerDoubleValue;
            case LESS_THAN_OR_EQUAL_TO:
                return deviceDoubleValue < triggerDoubleValue || deviceDoubleValue == triggerDoubleValue;
            case GREATER_THAN_OR_EQUAL_TO:
                return deviceDoubleValue > triggerDoubleValue || deviceDoubleValue == triggerDoubleValue;
            default:
                return false;
        }
    }

    /**
     * Part of redisplay logic
     *
     * If trigger key is part of message triggers, then return true, otherwise false
     * */
    boolean isTriggerOnMessage(OSInAppMessage message, Collection<String> newTriggersKeys) {
        if (message.triggers == null)
            return false;

        for (String triggerKey : newTriggersKeys) {
            for (ArrayList<OSTrigger> andConditions : message.triggers) {
                for (OSTrigger trigger : andConditions) {
                    // Dynamic triggers depends on triggerId
                    // Common triggers changed by user depends on property
                    if (triggerKey.equals(trigger.property) || triggerKey.equals(trigger.triggerId)) {
                        // At least one trigger has changed
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Part of redisplay logic
     *
     * If message has only dynamic trigger return true, otherwise false
     * */
    boolean messageHasOnlyDynamicTriggers(OSInAppMessage message) {
        if (message.triggers == null || message.triggers.isEmpty())
            return false;

        for (ArrayList<OSTrigger> andConditions : message.triggers) {
            for (OSTrigger trigger : andConditions) {
                if (trigger.kind == OSTrigger.OSTriggerKind.CUSTOM || trigger.kind == OSTrigger.OSTriggerKind.UNKNOWN)
                    // At least one trigger is not dynamic
                    return false;
            }
        }

        return true;
    }

    /**
     * Trigger Set/Delete/Persist Logic
     */
    void addTriggers(Map<String, Object> newTriggers) {
        synchronized (triggers) {
            for (String key : newTriggers.keySet()) {
                Object value = newTriggers.get(key);
                triggers.put(key, value);
            }
        }
    }

    void removeTriggersForKeys(Collection<String> keys) {
        synchronized (triggers) {
            for (String key : keys)
                triggers.remove(key);
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