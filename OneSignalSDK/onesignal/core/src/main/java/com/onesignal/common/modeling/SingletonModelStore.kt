package com.onesignal.common.modeling

import com.onesignal.common.events.EventProducer

/**
 * A singleton model store implementation that wraps a backing [ModelStore] to manage
 * the single [Model] instance.
 */
open class SingletonModelStore<TModel>(
    val store: ModelStore<TModel>,
) : ISingletonModelStore<TModel>, IModelStoreChangeHandler<TModel> where TModel : Model {
    private val changeSubscription: EventProducer<ISingletonModelStoreChangeHandler<TModel>> = EventProducer()
    private val singletonId: String = "-singleton-"

    private val replaceLock = Any()

    init {
        store.subscribe(this)
    }

    override val model: TModel
        get() {
            val model = store.get(singletonId)
            if (model != null) {
                return model
            }

            val createdModel = store.create() ?: throw Exception("Unable to initialize model from store $store")
            createdModel.id = singletonId
            store.add(createdModel)
            return createdModel
        }

    override fun replace(
        model: TModel,
        tag: String,
    ) {
        val existingModel = this.model
        existingModel.initializeFromModel(singletonId, model)
        store.persist()
        changeSubscription.fire { it.onModelReplaced(existingModel, tag) }
    }

    override fun subscribe(handler: ISingletonModelStoreChangeHandler<TModel>) = changeSubscription.subscribe(handler)

    override fun unsubscribe(handler: ISingletonModelStoreChangeHandler<TModel>) = changeSubscription.unsubscribe(handler)

    override val hasSubscribers: Boolean
        get() = changeSubscription.hasSubscribers

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
        changeSubscription.fire { it.onModelUpdated(args, tag) }
    }

    override fun onModelRemoved(
        model: TModel,
        tag: String,
    ) {
        // singleton is assumed to always exist. It never gets removed therefore no event.
    }
}
