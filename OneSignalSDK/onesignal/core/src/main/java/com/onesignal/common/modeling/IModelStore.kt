package com.onesignal.common.modeling

import com.onesignal.common.events.IEventNotifier
import org.json.JSONObject

/**
 * A model store provides access to the underlying models.  A model store is a key store, each model
 * in the model store must have a unique [Model.id] for retrieving a specific model.
 */
interface IModelStore<TModel> :
    IEventNotifier<IModelStoreChangeHandler<TModel>> where TModel : Model {
    /**
     * Create a new instance of the model and add it to the store. The new instance is *not* added
     * to the model store, because it may not yet have an [Model.id] which is required.
     *
     * @param jsonObject The optional [JSONObject] to initialize the new model with.
     */
    fun create(jsonObject: JSONObject? = null): TModel?

    /**
     * List the models that are owned by this model store.
     *
     * @return The collection of models.
     */
    fun list(): Collection<TModel>

    /**
     * Add a new model to this model store.  Once added, any changes to the
     * model will trigger calls to an [IModelStoreChangeHandler] that has
     * subscribed to this model store.  This same instance is also retrievable
     * via [get] based on [Model.id].
     *
     * @param model The model being added to the model store.
     * @param tag The tag which identifies how/why the model is being added.
     */
    fun add(
        model: TModel,
        tag: String = ModelChangeTags.NORMAL,
    )

    /**
     * Add a new model to this model store.  Once added, any changes to the
     * model will trigger calls to an [IModelStoreChangeHandler] that has
     * subscribed to this model store.  This same instance is also retrievable
     * via [get] based on [Model.id].
     *
     * @param index The index to add it under
     * @param model The model being added to the model store.
     * @param tag The tag which identifies how/why the model is being added.
     */
    fun add(
        index: Int,
        model: TModel,
        tag: String = ModelChangeTags.NORMAL,
    )

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
     * @param tag The tag which identifies how/why the model is being removed.
     */
    fun remove(
        id: String,
        tag: String = ModelChangeTags.NORMAL,
    )

    /**
     * Remove all models from the store.
     *
     * @param tag The tag which identifies how/why the model store is being cleared.
     */
    fun clear(tag: String = ModelChangeTags.NORMAL)

    /**
     * Replace all models in the store with the provided models.
     *
     * @param models The models to track in the model store.
     * @param tag The tag which identifies how/why the model store is being replaced.
     */
    fun replaceAll(
        models: List<TModel>,
        tag: String = ModelChangeTags.NORMAL,
    )
}

object ModelChangeTags {
    /**
     * A change was performed through normal means.
     */
    const val NORMAL = "NORMAL"

    /**
     * A change was performed that should *not* be propogated to the backend.
     */
    const val NO_PROPOGATE = "NO_PROPOGATE"

    /**
     * A change was performed through the backend hydrating the model.
     */
    const val HYDRATE = "HYDRATE"
}
