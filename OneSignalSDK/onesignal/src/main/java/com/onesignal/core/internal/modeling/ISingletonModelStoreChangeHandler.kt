package com.onesignal.core.internal.modeling

/**
 * A handler interface for [ISingletonModelStore.subscribe]. Implement this interface to subscribe
 * to model change events for a specific model store.
 */
internal interface ISingletonModelStoreChangeHandler<TModel> where TModel : Model {

    /**
     * Called when the model has been replaced.
     *
     * @param model The model
     */
    fun onModelReplaced(model: TModel)

    /**
     * Called when a property within the model has been updated.
     *
     * @param model The model that has been updated (includes the updates).
     * @param property The property of the model that has been updated.
     * @param oldValue The old value of the property that has changed.
     * @param newValue The new value of the property that has changed.
     */
    fun onModelUpdated(model: TModel, path: String, property: String, oldValue: Any?, newValue: Any?)
}
