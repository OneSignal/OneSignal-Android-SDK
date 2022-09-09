package com.onesignal.core.internal.modeling

import com.onesignal.core.internal.common.events.EventProducer
import com.onesignal.core.internal.common.events.IEventNotifier

/**
 * The implementation of a model store.
 */
internal open class ModelStore<TModel>(
    private val _changeSubscription: EventProducer<IModelStoreChangeHandler<TModel>> = EventProducer()
) : IEventNotifier<IModelStoreChangeHandler<TModel>> by _changeSubscription, IModelStore<TModel>, IModelChangedHandler where TModel : Model {

    private val _models: HashMap<String, TModel> = HashMap()

    override fun add(id: String, model: TModel, fireEvent: Boolean) {
        _models[id] = model

        // TODO: Persist the new model to storage.

        // listen for changes to this model
        model.subscribe(this)

        if (fireEvent) {
            _changeSubscription.fire { it.onAdded(model) }
        }
    }

    override fun list(): Collection<String> {
        return _models.keys
    }

    override fun get(id: String): TModel? {
        return _models[id]
    }

    override fun remove(id: String) {
        val model = _models[id] ?: return

        _models.remove(id)

        // no longer listen for changes to this model
        model.unsubscribe(this)

        // TODO: Remove the model from storage

        _changeSubscription.fire { it.onRemoved(model) }
    }

    override fun clear() {
        val localList = _models.toList()
        _models.clear()

        for (item in localList) {
            _changeSubscription.fire { it.onRemoved(item.second) }
        }
    }

    override fun onChanged(args: ModelChangedArgs) {
        // TODO: Persist the changed model to storage. Consider batching.

        _changeSubscription.fire { it.onUpdated(args.model as TModel, args.property, args.oldValue, args.newValue) }
    }
}
