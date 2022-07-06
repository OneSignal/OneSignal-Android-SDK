package com.onesignal.onesignal.internal.modeling

import com.onesignal.onesignal.internal.common.events.IEventNotifier

/**
 * A model store that expects to only store a single instance
 * of a model. It behaves like an [IModelStore] with the exception that
 * there are no IDs, and there is an expectation that the (one) model
 * always exists.
 */
interface ISingletonModelStore<TModel> :
    IEventNotifier<ISingletonModelStoreChangeHandler<TModel>> where TModel : Model {
    /**
     * Retrieve the model managed by this singleton model store.
     *
     * @return The single model managed by this store.
     */
    fun get() : TModel
}