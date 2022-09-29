package com.onesignal.core.internal.modeling

/**
 * A model store that is implemented in memory.
 */
internal open class SingletonModelStore<TModel>(
    val name: String,
    private val create: () -> TModel,
    val store: IModelStore<TModel>
) : ISingletonModelStore<TModel>, IModelChangedHandler where TModel : Model {

    private val _subscribers: MutableList<ISingletonModelStoreChangeHandler<TModel>> = mutableListOf()

    override fun get(): TModel {
        val model = store.get(name)
        if (model != null) {
            return model
        }

        val createdModel = create()
        createdModel.id = name
        createdModel.subscribe(this)
        store.add(createdModel)
        return createdModel
    }

    override fun replace(model: TModel, fireEvent: Boolean) {
        val oldModel = get()
        oldModel.data.clear()
        oldModel.data.putAll(model.data)
        oldModel.setProperty(Model::id.name, name)

        for (s in _subscribers) {
            s.onModelReplaced(oldModel)
        }
    }

    override fun subscribe(handler: ISingletonModelStoreChangeHandler<TModel>) {
        _subscribers.add(handler)
    }

    override fun unsubscribe(handler: ISingletonModelStoreChangeHandler<TModel>) {
        _subscribers.remove(handler)
    }

    override fun onChanged(args: ModelChangedArgs) {
        for (s in _subscribers) {
            s.onModelUpdated(args.model as TModel, args.path, args.property, args.oldValue, args.newValue)
        }
    }
}
