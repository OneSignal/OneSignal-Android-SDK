package com.onesignal.inAppMessages.internal.triggers.impl

import com.onesignal.common.modeling.IModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.internal.InAppMessage
import com.onesignal.inAppMessages.internal.Trigger
import com.onesignal.inAppMessages.internal.triggers.ITriggerController
import com.onesignal.inAppMessages.internal.triggers.ITriggerHandler
import com.onesignal.inAppMessages.internal.triggers.TriggerModel
import com.onesignal.inAppMessages.internal.triggers.TriggerModelStore
import java.text.DecimalFormat
import java.util.concurrent.ConcurrentHashMap

internal class TriggerController(
    triggerModelStore: TriggerModelStore,
    private var _dynamicTriggerController: DynamicTriggerController,
) : ITriggerController, IModelStoreChangeHandler<TriggerModel> {
    val triggers: ConcurrentHashMap<String?, Any?> = ConcurrentHashMap()

    init {
        triggerModelStore.subscribe(this)
    }

    override fun evaluateMessageTriggers(message: InAppMessage): Boolean {
        // If there are no triggers then we display the In-App when a new session is triggered
        if (message.triggers.isEmpty()) {
            return true
        }

        // Outer loop represents OR conditions
        for (andConditions in message.triggers) {
            if (evaluateAndTriggers(andConditions)) {
                return true
            }
        }

        return false
    }

    private fun evaluateAndTriggers(andConditions: List<Trigger>): Boolean {
        for (trigger in andConditions) {
            if (!evaluateTrigger(trigger)) {
                return false
            }
        }

        return true
    }

    private fun evaluateTrigger(trigger: Trigger): Boolean {
        // Assume all unknown trigger kinds to be false to be safe.
        if (trigger.kind == Trigger.OSTriggerKind.UNKNOWN) {
            return false
        }

        if (trigger.kind != Trigger.OSTriggerKind.CUSTOM) {
            return _dynamicTriggerController.dynamicTriggerShouldFire(trigger)
        }

        val operatorType = trigger.operatorType
        // If trigger is type of NOT_EQUAL_TO, true if only there exists a trigger with the same key and different value.
        // In that case, do not return early so it will evaluate later.
        val deviceValue =
            triggers[trigger.property]
                ?: // If we don't have a local value for this trigger, can only be true if operator is Not Exists
                return operatorType == Trigger.OSTriggerOperator.NOT_EXISTS

        // We have local value at this point, we can evaluate existence checks
        if (operatorType == Trigger.OSTriggerOperator.EXISTS) {
            return true
        }
        if (operatorType == Trigger.OSTriggerOperator.NOT_EXISTS) {
            return false
        }
        if (operatorType == Trigger.OSTriggerOperator.CONTAINS) {
            return deviceValue is Collection<*> && deviceValue.contains(trigger.value)
        }
        if (deviceValue is String &&
            trigger.value is String &&
            triggerMatchesStringValue((trigger.value as String?)!!, deviceValue, operatorType)
        ) {
            return true
        }
        if (trigger.value is Number &&
            deviceValue is Number &&
            triggerMatchesNumericValue((trigger.value as Number?)!!, deviceValue, operatorType)
        ) {
            return true
        }

        return triggerMatchesFlex(trigger.value, deviceValue, operatorType)
    }

    private fun triggerMatchesStringValue(
        triggerValue: String,
        deviceValue: String,
        operator: Trigger.OSTriggerOperator,
    ): Boolean {
        return when (operator) {
            Trigger.OSTriggerOperator.EQUAL_TO -> triggerValue == deviceValue
            Trigger.OSTriggerOperator.NOT_EQUAL_TO -> triggerValue != deviceValue
            else -> {
                Logging.error("Attempted to use an invalid operator for a string trigger comparison: $operator")
                false
            }
        }
    }

    // Allow converting of deviceValues to other types to allow triggers to be more forgiving.
    private fun triggerMatchesFlex(
        triggerValue: Any?,
        deviceValue: Any,
        operator: Trigger.OSTriggerOperator,
    ): Boolean {
        if (triggerValue == null) return false

        // If operator is equal or not equals ignore type by comparing on toString values
        if (operator.checksEquality()) {
            val triggerValueString = triggerValue.toString()
            var deviceValueString = deviceValue.toString()
            if (deviceValue is Number) {
                // User may have an input text that converts 5 to 5.0, we only care about the raw value on equals
                val format = DecimalFormat("0.#")
                deviceValueString = format.format(deviceValue)
            }
            return triggerMatchesStringValue(triggerValueString, deviceValueString, operator)
        }
        return if (deviceValue is String &&
            triggerValue is Number
        ) {
            triggerMatchesNumericValueFlex(
                triggerValue,
                deviceValue,
                operator,
            )
        } else {
            false
        }
    }

    private fun triggerMatchesNumericValueFlex(
        triggerValue: Number,
        deviceValue: String,
        operator: Trigger.OSTriggerOperator,
    ): Boolean {
        val deviceDoubleValue: Double =
            try {
                deviceValue.toDouble()
            } catch (e: NumberFormatException) {
                return false
            }
        return triggerMatchesNumericValue(triggerValue.toDouble(), deviceDoubleValue, operator)
    }

    private fun triggerMatchesNumericValue(
        triggerValue: Number,
        deviceValue: Number,
        operator: Trigger.OSTriggerOperator,
    ): Boolean {
        val triggerDoubleValue = triggerValue.toDouble()
        val deviceDoubleValue = deviceValue.toDouble()
        return when (operator) {
            Trigger.OSTriggerOperator.EXISTS, Trigger.OSTriggerOperator.CONTAINS, Trigger.OSTriggerOperator.NOT_EXISTS -> {
                Logging.error("Attempted to use an invalid operator with a numeric value: $operator")
                false
            }
            Trigger.OSTriggerOperator.EQUAL_TO -> deviceDoubleValue == triggerDoubleValue
            Trigger.OSTriggerOperator.NOT_EQUAL_TO -> deviceDoubleValue != triggerDoubleValue
            Trigger.OSTriggerOperator.LESS_THAN -> deviceDoubleValue < triggerDoubleValue
            Trigger.OSTriggerOperator.GREATER_THAN -> deviceDoubleValue > triggerDoubleValue
            Trigger.OSTriggerOperator.LESS_THAN_OR_EQUAL_TO -> deviceDoubleValue < triggerDoubleValue || deviceDoubleValue == triggerDoubleValue
            Trigger.OSTriggerOperator.GREATER_THAN_OR_EQUAL_TO -> deviceDoubleValue > triggerDoubleValue || deviceDoubleValue == triggerDoubleValue
        }
    }

    override fun isTriggerOnMessage(
        message: InAppMessage,
        triggersKeys: Collection<String>,
    ): Boolean {
        if (message.triggers == null) {
            return false
        }

        for (triggerKey in triggersKeys) {
            for (andConditions in message.triggers) {
                for (trigger in andConditions) {
                    // Dynamic triggers depends on triggerId
                    // Common triggers changed by user depends on property
                    if (triggerKey == trigger.property || triggerKey == trigger.triggerId) {
                        // At least one trigger has changed
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun messageHasOnlyDynamicTriggers(message: InAppMessage): Boolean {
        if (message.triggers == null || message.triggers.isEmpty()) return false
        for (andConditions in message.triggers) {
            for (trigger in andConditions) {
                if (trigger.kind == Trigger.OSTriggerKind.CUSTOM || trigger.kind == Trigger.OSTriggerKind.UNKNOWN) {
                    // At least one trigger is not dynamic
                    return false
                }
            }
        }
        return true
    }

    override fun onModelAdded(
        model: TriggerModel,
        tag: String,
    ) {
        addTriggers(model.key, model.value)
        _dynamicTriggerController.events.fire { it.onTriggerChanged(model.key) }
    }

    override fun onModelUpdated(
        args: ModelChangedArgs,
        tag: String,
    ) {
        val model = args.model as TriggerModel
        addTriggers(model.key, model.value)
        _dynamicTriggerController.events.fire { it.onTriggerChanged(model.key) }
    }

    override fun onModelRemoved(
        model: TriggerModel,
        tag: String,
    ) {
        removeTriggersForKeys(model.key)
    }

    private fun addTriggers(
        key: String,
        value: Any,
    ) {
        synchronized(triggers) {
            triggers[key] = value
        }
    }

    private fun removeTriggersForKeys(key: String) {
        synchronized(triggers) { triggers.remove(key) }
    }

    override fun subscribe(handler: ITriggerHandler) = _dynamicTriggerController.subscribe(handler)

    override fun unsubscribe(handler: ITriggerHandler) = _dynamicTriggerController.unsubscribe(handler)

    override val hasSubscribers: Boolean
        get() = _dynamicTriggerController.hasSubscribers
}
