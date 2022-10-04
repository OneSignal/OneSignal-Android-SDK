package com.onesignal.core.internal.modeling

import com.onesignal.core.internal.common.events.IEventNotifier

/**
 * A model store that expects to only contain a single [Model] within it.  It behaves like an
 * [IModelStore] with the exception that there are no IDs, and there is an expectation that the
 * (one) model always exists.
 */
internal interface ISingletonModelStore<TModel> :
    IEventNotifier<ISingletonModelStoreChangeHandler<TModel>> where TModel : Model {
    /**
     * Retrieve the model managed by this singleton model store.
     *
     * @return The single model managed by this store.
     */
    fun get(): TModel

    /**
     * Replace the existing model with the new model provided.
     *
     * @param model A model that contains all the data for the new effective model.
     * @param fireEvent Whether an event should be fired for this update action.
     */
    fun replace(model: TModel, fireEvent: Boolean = true)
}
