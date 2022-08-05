package com.onesignal.onesignal.core.internal.modeling

import com.onesignal.onesignal.core.internal.common.events.IEventNotifier

/**
 * A model store provides access to the underlying models.  A model is
 * automatically persisted locally across the application lifecycle by
 * the model store itself.  A model store is a key store, each model
 * in the model store must have a unique identifier for retrieving
 * a specific model.
 */
interface IModelStore<TModel> : IEventNotifier<IModelStoreChangeHandler<TModel>> where TModel : Model {

    /**
     * List the keys of the models that are owned by this model store.
     *
     * @return The collection of keys.
     */
    fun list() : Collection<String>

    /**
     * Add a new model to this model store.  Once added, any changes to the
     * model will trigger calls to an [IModelStoreChangeHandler] that has
     * subscribed to this model store.  This same instance is also retrievable
     * via [get].
     *
     * @param id The unique identifier for the model being added.
     * @param model The model being added to the model store.
     * @param fireChange Whether an event should be fired, defaults to true.
     */
    fun add(id: String, model: TModel, fireEvent: Boolean = true)

    /**
     * Retrieve the model associated to the id provided.
     *
     * @param id The unique identifier for the model to retrieve.
     *
     * @return The model associated to the id provided, or null if no such model exists.
     */
    fun get(id: String) : TModel?

    /**
     * Remove the model associated to the id provided from the model store.
     *
     * @param id The unique identifier to the model to remove.
     */
    fun remove(id: String)

    /**
     * Clear all models within the model store.
     */
    fun clear()
}