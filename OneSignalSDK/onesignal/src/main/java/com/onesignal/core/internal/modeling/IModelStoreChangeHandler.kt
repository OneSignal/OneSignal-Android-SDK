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
     * @param tag The tag which identifies how/why the model was added.
     */
    fun onModelAdded(model: TModel, tag: String)

    /**
     * Called when a model has been updated.  This callback wraps [IModelChangedHandler.onChanged]
     * so users of the model store does not need to manage subscriptions to each individual [Model]
     * within the store.
     *
     * @param args The model changed arguments.
     * @param tag The tag which identifies how/why the model was updated.
     */
    fun onModelUpdated(args: ModelChangedArgs, tag: String)

    /**
     * Called when a model has been removed from the model store.
     *
     * @param model The model that has been removed.
     * @param tag The tag which identifies how/why the model was removed.
     */
    fun onModelRemoved(model: TModel, tag: String)
}
