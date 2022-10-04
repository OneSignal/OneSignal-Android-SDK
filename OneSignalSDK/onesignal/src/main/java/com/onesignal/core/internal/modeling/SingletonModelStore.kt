package com.onesignal.core.internal.modeling

import com.onesignal.core.internal.common.events.EventProducer

/**
 * A singleton model store implementation that wraps a backing [ModelStore] to manage
 * the single [Model] instance.
 */
internal open class SingletonModelStore<TModel>(
    val store: ModelStore<TModel>
) : ISingletonModelStore<TModel>, IModelStoreChangeHandler<TModel> where TModel : Model {

    private val _changeSubscription: EventProducer<ISingletonModelStoreChangeHandler<TModel>> = EventProducer()
    private val _singletonId: String = "-singleton-"

    init {
        store.subscribe(this)
    }

    override fun get(): TModel {
        val model = store.get(_singletonId)
        if (model != null) {
            return model
        }

        val createdModel = store.create()
        createdModel.id = _singletonId
        store.add(createdModel)
        return createdModel
    }

    override fun replace(model: TModel, fireEvent: Boolean) {
        val existingModel = get()
        existingModel.initializeFromModel(model)
        existingModel.setProperty(Model::id.name, _singletonId, notify = false)

        _changeSubscription.fire { it.onModelReplaced(existingModel) }
    }

    /**
     * Persist the singleton model store.
     */
    fun persist() {
        store.persist()
    }

    override fun subscribe(handler: ISingletonModelStoreChangeHandler<TModel>) = _changeSubscription.subscribe(handler)
    override fun unsubscribe(handler: ISingletonModelStoreChangeHandler<TModel>) = _changeSubscription.unsubscribe(handler)

    override fun onAdded(model: TModel) {
        // singleton is assumed to always exist. It gets added transparently therefore no event.
    }

    override fun onUpdated(model: TModel, path: String, property: String, oldValue: Any?, newValue: Any?) {
        _changeSubscription.fire { it.onModelUpdated(model, path, property, oldValue, newValue) }
    }

    override fun onRemoved(model: TModel) {
        // singleton is assumed to always exist. It never gets removed therefore no event.
    }
}
