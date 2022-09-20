package com.onesignal.core.internal.modeling

import com.onesignal.core.internal.common.events.IEventNotifier

internal interface IModelChangedHandler {
    fun onChanged(args: ModelChangedArgs)
}

/**
 * The arguments passed to the [IEventNotifier] handler when subscribed via [Model.subscribe]
 */
internal class ModelChangedArgs(
    /**
     * The full model in its current state.
     */
    val model: Model,

    /**
     * The path to the property, from the root, that has changed.  When the root model has
     * changed, [path] and [property] will be identical.  When it's a nested property that
     * has changed, [path] will contain a "dot notation" path to the property:
     *
     * * If a map property on the root model gets a new KVP, this will be `mapProperty.new_key`
     * * If a complex property on the root model, this will be `complexProperty.simpleProperty`
     * * If a map property on a complex property on the root model gets a new KVP, this will be `complexProperty.mapProperty.new_key`
     *
     */
    val path: String,

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
