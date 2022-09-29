package com.onesignal.core.internal.modeling

/**
 * A handler interface for [IModelStore.subscribe]. Implement this interface to subscribe
 * to model change events for a specific model store.
 */
internal interface IModelStoreChangeHandler<TModel> where TModel : Model {
    /**
     * Called when a model has been added to the model store.
     *
     * @param model The model that has been added.
     */
    fun onAdded(model: TModel)

    /**
     * Called when a model has been updated.  This callback wraps [IModelChangedHandler.onChanged]
     * so users of the model store does not need to manage subscriptions to each individual [Model]
     * within the store.
     *
     * @param model The model that has been updated (includes the updates).
     * @param path The path to the property of the model that has been updated.
     * @param property The property of the model that has been updated.
     * @param oldValue The old value of the property that has changed.
     * @param newValue The new value of the property that has changed.
     */
    fun onUpdated(model: TModel, path: String, property: String, oldValue: Any?, newValue: Any?)

    /**
     * Called when a model has been removed from the model store.
     *
     * @param model The model that has been removed.
     */
    fun onRemoved(model: TModel)
}
