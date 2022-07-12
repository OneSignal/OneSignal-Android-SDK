package com.onesignal.onesignal.core.internal.modeling

/**
 * A handler interface for [ISingletonModelStore.subscribe]. Implement this interface to subscribe
 * to model change events for a specific model store.
 */
interface ISingletonModelStoreChangeHandler<TModel> where TModel : Model {

    /**
     * Called when a model has been updated.
     *
     * @param model The model that has been updated (includes the updates).
     * @param property The property of the model that has been updated.
     * @param oldValue The old value of the property that has changed.
     * @param newValue The new value of the property that has changed.
     */
    fun updated(model: TModel, property: String, oldValue: Any?, newValue: Any?)
}