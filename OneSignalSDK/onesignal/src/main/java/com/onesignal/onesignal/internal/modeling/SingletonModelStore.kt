package com.onesignal.onesignal.internal.modeling

/**
 * A model store that is implemented in memory.
 */
internal class SingletonModelStore<TModel>(
        val name: String,
        val store: IModelStore<TModel>,
        ) : ISingletonModelStore<TModel> where TModel : Model {

    override fun get(): TModel? {
        return store.get(name)
    }

    override fun update(model: TModel) {
        store.update(name, model)
    }

    override fun subscribe(handler: IModelStoreChangeHandler<TModel>) {
        TODO("Not yet implemented")
    }

    override fun unsubscribe(handler: IModelStoreChangeHandler<TModel>) {
        TODO("Not yet implemented")
    }
}