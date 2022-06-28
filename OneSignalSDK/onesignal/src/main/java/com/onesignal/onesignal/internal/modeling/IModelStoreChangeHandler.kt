package com.onesignal.onesignal.internal.modeling

/**
 * A handler interface for [IModelStore.subscribe]. Implement this interface to subscribe
 * to model change events for a specific model store.
 */
interface IModelStoreChangeHandler<TModel> where TModel : Model {
    /**
     * Called when a model has been added to the model store.
     *
     * @param model The model that has been added.
     */
    fun added(model: TModel)

    /**
     * Called when a model has been updated.
     *
     * @param model The model that has been updated (includes the updates).
     * @param property The property of the model that has been updated.
     * @param oldValue The old value of the property that has changed.
     * @param newValue The new value of the property that has changed.
     */
    fun updated(model: TModel, property: String, oldValue: Any?, newValue: Any?)

    /**
     * Called when a model has been removed from the model store.
     *
     * @param model The model that has been removed.
     */
    fun removed(model: TModel)
}