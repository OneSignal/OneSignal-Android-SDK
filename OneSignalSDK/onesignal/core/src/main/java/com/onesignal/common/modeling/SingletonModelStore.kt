package com.onesignal.common.modeling

import com.onesignal.common.events.EventProducer

/**
 * A singleton model store implementation that wraps a backing [ModelStore] to manage
 * the single [Model] instance.
 */
open class SingletonModelStore<TModel>(
    val store: ModelStore<TModel>,
) : ISingletonModelStore<TModel>, IModelStoreChangeHandler<TModel> where TModel : Model {
    private val _changeSubscription: EventProducer<ISingletonModelStoreChangeHandler<TModel>> = EventProducer()
    private val _singletonId: String = "-singleton-"

    init {
        store.subscribe(this)
    }

    override val model: TModel
        get() {
            val model = store.get(_singletonId)
            if (model != null) {
                return model
            }

            val createdModel = store.create() ?: throw Exception("Unable to initialize model from store $store")
            createdModel.id = _singletonId
            store.add(createdModel)
            return createdModel
        }

    override fun replace(
        model: TModel,
        tag: String,
    ) {
        val existingModel = this.model
        existingModel.initializeFromModel(_singletonId, model)
        store.persist()
        _changeSubscription.fire { it.onModelReplaced(existingModel, tag) }
    }

    override fun subscribe(handler: ISingletonModelStoreChangeHandler<TModel>) = _changeSubscription.subscribe(handler)

    override fun unsubscribe(handler: ISingletonModelStoreChangeHandler<TModel>) = _changeSubscription.unsubscribe(handler)

    override val hasSubscribers: Boolean
        get() = _changeSubscription.hasSubscribers

    override fun onModelAdded(
        model: TModel,
        tag: String,
    ) {
        // singleton is assumed to always exist. It gets added transparently therefore no event.
    }

    override fun onModelUpdated(
        args: ModelChangedArgs,
        tag: String,
    ) {
        _changeSubscription.fire { it.onModelUpdated(args, tag) }
    }

    override fun onModelRemoved(
        model: TModel,
        tag: String,
    ) {
        // singleton is assumed to always exist. It never gets removed therefore no event.
    }
}
