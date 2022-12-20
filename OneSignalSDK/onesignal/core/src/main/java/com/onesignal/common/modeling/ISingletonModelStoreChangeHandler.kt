package com.onesignal.common.modeling

/**
 * A handler interface for [ISingletonModelStore.subscribe]. Implement this interface to subscribe
 * to model change events for a specific model store.
 */
interface ISingletonModelStoreChangeHandler<TModel> where TModel : Model {

    /**
     * Called when the model has been replaced.
     *
     * @param model The model
     * @param tag The tag which identifies how/why the model was replaced.
     */
    fun onModelReplaced(model: TModel, tag: String)

    /**
     * Called when a property within the model has been updated. This callback wraps [IModelChangedHandler.onChanged]
     * so users of the model store does not need to manage subscriptions to the individual [Model]
     * within the store.
     *
     * @param args The model changed arguments.
     * @param tag The tag which identifies how/why the model was updated.
     */
    fun onModelUpdated(args: ModelChangedArgs, tag: String)
}
