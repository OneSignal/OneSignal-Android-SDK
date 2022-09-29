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
     * Called when a property within the model has been updated. This callback wraps [IModelChangedHandler.onChanged]
     * so users of the model store does not need to manage subscriptions to the individual [Model]
     * within the store.
     *
     * @param model The model that has been updated (includes the updates).
     * @param path The path to the property of the model that has been updated.
     * @param property The property of the model that has been updated.
     * @param oldValue The old value of the property that has changed.
     * @param newValue The new value of the property that has changed.
     */
    fun onModelUpdated(model: TModel, path: String, property: String, oldValue: Any?, newValue: Any?)
}
