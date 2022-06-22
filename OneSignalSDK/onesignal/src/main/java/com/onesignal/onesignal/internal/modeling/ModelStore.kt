package com.onesignal.onesignal.internal.modeling

import com.onesignal.onesignal.internal.common.INotifyChangedHandler

/**
 * The implementation of a model store.
 */
internal class ModelStore<TModel> : IModelStore<TModel>,
    INotifyChangedHandler<ModelChangedArgs> where TModel : Model {

    private val _models: HashMap<String, TModel> = HashMap()
    private val _subscribers: MutableList<IModelStoreChangeHandler<TModel>> = mutableListOf()

    override fun add(id: String, model: TModel) {
        _models[id] = model

        // TODO: Persist the new model to storage.

        // listen for changes to this model
        model.subscribe(this)

        // notify any change listeners of the added model
        for(s in _subscribers) {
            s.added(model)
        }
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

        // notify any change listeners of the removed model
        for(s in _subscribers) {
            s.removed(model)
        }
    }

    override fun subscribe(handler: IModelStoreChangeHandler<TModel>) {
        _subscribers.add(handler)
    }

    override fun unsubscribe(handler: IModelStoreChangeHandler<TModel>) {
        _subscribers.remove(handler)
    }

    override fun onChanged(args: ModelChangedArgs) {
        // TODO: Persist the changed model to storage. Consider batching.

        for(s in _subscribers) {
            s.updated(args.model as TModel, args.property, args.oldValue, args.newValue)
        }
    }
}