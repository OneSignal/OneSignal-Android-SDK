package com.onesignal.common.modeling

import com.onesignal.common.events.IEventNotifier

/**
 * A model store that expects to only contain a single [Model] within it.  It behaves like an
 * [IModelStore] with the exception that there are no IDs, and there is an expectation that the
 * (one) model always exists.
 */
interface ISingletonModelStore<TModel> :
    IEventNotifier<ISingletonModelStoreChangeHandler<TModel>> where TModel : Model {
    /**
     * The model managed by this singleton model store.
     */
    val model: TModel

    /**
     * Replace the existing model with the new model provided.
     *
     * @param model A model that contains all the data for the new effective model.
     * @param tag The tag which identifies how/why the model is being replaced.
     */
    fun replace(model: TModel, tag: String = ModelChangeTags.NORMAL)
}
