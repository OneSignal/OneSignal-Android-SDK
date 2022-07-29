package com.onesignal.onesignal.core.internal.modeling

import com.onesignal.onesignal.core.internal.common.events.IEventNotifier

interface IModelChangedHandler {
    fun onChanged(args: ModelChangedArgs)
}

/**
 * The arguments passed to the [IEventNotifier] handler when subscribed via [Model.subscribe]
 */
class ModelChangedArgs(
    /**
     * The full model in its current state.
     */
    val model: Model,

    /**
     * The property that was changed.
     */
    val property: String,

    /**
     * The old value of the property, prior to it being changed.
     */
    val oldValue: Any?,

    /**
     * The new value of the property, after it has been changed.
     */
    val newValue: Any?
)