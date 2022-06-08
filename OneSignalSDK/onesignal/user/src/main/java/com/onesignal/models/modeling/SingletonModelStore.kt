package com.onesignal.models.modeling

/**
 * A model store that is implemented in memory.
 */
internal class SingletonModelStore<TModel>(
        val name: String,
        val store: IModelStore<TModel>,
        ) : ISingletonModelStore<TModel> where TModel : Model {
    override fun create(model: TModel) {
        store.create(name, model)
    }

    override fun get(): TModel? {
        return store.get(name)
    }

    override fun update(model: TModel) {
        store.update(name, model)
    }

    override fun delete() {
        store.delete(name)
    }
}