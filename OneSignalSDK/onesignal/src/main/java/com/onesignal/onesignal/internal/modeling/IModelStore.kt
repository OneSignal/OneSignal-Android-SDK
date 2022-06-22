package com.onesignal.onesignal.internal.modeling

import com.onesignal.onesignal.internal.common.INotifyChanged

/**
 * A model store provides access to the underlying models.  A model is
 * automatically persisted locally across the application lifecycle by
 * the model store itself.  A model store is a key store, each model
 * in the model store must have a unique identifier for retrieving
 * a specific model.
 */
interface IModelStore<TModel> : INotifyChanged<IModelStoreChangeHandler<TModel>> where TModel : Model {

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
     */
    fun add(id: String, model: TModel)

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
}

/**
 * A model store that expects to only store a single instance
 * of a model. It behaves like an [IModelStore] with the exception that
 * there are no IDs, and there is an expectation that the (one) model
 * always exists.
 */
interface ISingletonModelStore<TModel> : INotifyChanged<ISingletonModelStoreChangeHandler<TModel>> where TModel : Model {
    /**
     * Retrieve the model managed by this singleton model store.
     *
     * @return The single model managed by this store.
     */
    fun get() : TModel
}

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

/**
 * A handler interface for [ISingletonModelStore.subscribe]. Implement this interface to subscribe
 * to model change events for a specific model store.
 */
interface ISingletonModelStoreChangeHandler<TModel> where TModel : Model {

    /**
     * Called when a model has been updated.
     *
     * @param model The model that has been updated (includes the updates).
     * @param property The property of the model that has been updated.
     * @param oldValue The old value of the property that has changed.
     * @param newValue The new value of the property that has changed.
     */
    fun updated(model: TModel, property: String, oldValue: Any?, newValue: Any?)
}