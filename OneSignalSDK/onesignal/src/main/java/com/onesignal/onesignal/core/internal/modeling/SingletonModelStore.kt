package com.onesignal.onesignal.core.internal.modeling

/**
 * A model store that is implemented in memory.
 */
internal open class SingletonModelStore<TModel>(
        val name: String,
        private val create: () -> TModel,
        val store: IModelStore<TModel>,
        ) : ISingletonModelStore<TModel>, IModelChangedHandler where TModel : Model {

    private val _subscribers: MutableList<ISingletonModelStoreChangeHandler<TModel>> = mutableListOf()

    override fun get(): TModel {
        val model = store.get(name)
        if(model != null)
            return model

        val createdModel = create()
        createdModel.subscribe(this)
        store.add(name, createdModel)
        return createdModel
    }

    override fun subscribe(handler: ISingletonModelStoreChangeHandler<TModel>) {
        _subscribers.add(handler)
    }

    override fun unsubscribe(handler: ISingletonModelStoreChangeHandler<TModel>) {
        _subscribers.remove(handler)
    }

    override fun onChanged(args: ModelChangedArgs) {
        for(s in _subscribers) {
            s.onModelUpdated(args.model as TModel, args.property, args.oldValue, args.newValue)
        }
    }
}