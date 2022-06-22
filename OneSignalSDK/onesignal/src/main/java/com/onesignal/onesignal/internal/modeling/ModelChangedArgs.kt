package com.onesignal.onesignal.internal.modeling

import com.onesignal.onesignal.internal.common.INotifyChanged

/**
 * The arguments passed to the [INotifyChanged] handler when subscribed via [Model.subscribe]
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