package com.onesignal.core.internal.modeling

import com.onesignal.core.internal.common.events.IEventNotifier
import org.json.JSONObject

/**
 * A model store provides access to the underlying models.  A model store is a key store, each model
 * in the model store must have a unique [Model.id] for retrieving a specific model.
 */
internal interface IModelStore<TModel> : IEventNotifier<IModelStoreChangeHandler<TModel>> where TModel : Model {

    /**
     * Create a new instance of the model and add it to the store. The new instance is *not* added
     * to the model store, because it may not yet have an [Model.id] which is required.
     *
     * @param jsonObject The optional [JSONObject] to initialize the new model with.
     */
    fun create(jsonObject: JSONObject? = null): TModel

    /**
     * List the keys of the models that are owned by this model store.
     *
     * @return The collection of keys.
     */
    fun list(): Collection<TModel>

    /**
     * Add a new model to this model store.  Once added, any changes to the
     * model will trigger calls to an [IModelStoreChangeHandler] that has
     * subscribed to this model store.  This same instance is also retrievable
     * via [get].
     *
     * @param model The model being added to the model store.
     * @param fireEvent Whether an event should be fired, defaults to true.
     */
    fun add(model: TModel, fireEvent: Boolean = true)

    /**
     * Retrieve the model associated to the id provided.
     *
     * @param id The unique identifier for the model to retrieve.
     *
     * @return The model associated to the id provided, or null if no such model exists.
     */
    fun get(id: String): TModel?

    /**
     * Remove the model associated to the id provided from the model store.
     *
     * @param id The unique identifier to the model to remove.
     * @param fireEvent Whether an event should be fired, defaults to true.
     */
    fun remove(id: String, fireEvent: Boolean = true)

    /**
     * Remove all models from the store.
     */
    fun clear(fireEvent: Boolean = true)

    /**
     * Replace all models in the store with the provided models.
     *
     * @param models The models to track in the model store.
     * @param fireEvent Whether an event should be fired, defaults to true.
     */
    fun replaceAll(models: List<TModel>, fireEvent: Boolean = true)
}
