package com.onesignal.onesignal.internal.modeling

import com.onesignal.onesignal.internal.common.IEventNotifier
import com.onesignal.onesignal.internal.common.EventProducer

/**
 * The implementation of a model store.
 */
internal class ModelStore<TModel>(
    private val _changeSubscription: EventProducer<IModelStoreChangeHandler<TModel>> = EventProducer()
) : IEventNotifier<IModelStoreChangeHandler<TModel>> by _changeSubscription, IModelStore<TModel>, IModelChangedHandler where TModel : Model {

    private val _models: HashMap<String, TModel> = HashMap()


    override fun add(id: String, model: TModel) {
        _models[id] = model

        // TODO: Persist the new model to storage.

        // listen for changes to this model
        model.subscribe(this)

        _changeSubscription.fire { it.added(model) }
    }

    override fun list() : Collection<String> {
        return _models.keys
    }

    override fun get(id: String) : TModel? {
        return _models[id]
    }

    override fun remove(id: String) {
        val model = _models[id] ?: return

        _models.remove(id)

        // no longer listen for changes to this model
        model.unsubscribe(this)

        // TODO: Remove the model from storage

        _changeSubscription.fire { it.removed(model) }
    }

    override fun onChanged(args: ModelChangedArgs) {
        // TODO: Persist the changed model to storage. Consider batching.

        _changeSubscription.fire { it.updated(args.model as TModel, args.property, args.oldValue, args.newValue) }
    }
}