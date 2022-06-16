package com.onesignal.onesignal.internal.modeling

/**
 * A model store that is implemented in memory.
 */
internal class MemoryModelStore<TModel> : IModelStore<TModel> where TModel : Model {
    private val cache: HashMap<String, TModel> = HashMap()

    override fun create(id: String, model: TModel) {
        cache[id] = model
    }

    override fun list() : Collection<TModel> {
        return cache.values
    }

    override fun get(id: String) : TModel? {
        return cache[id]
    }

    override fun update(id: String, model: TModel) {
        cache[id] = model
    }

    override fun delete(id: String) {
        cache.remove(id)
    }

    override fun subscribe(handler: IModelStoreChangeHandler<TModel>) {
        TODO("Not yet implemented")
    }

    override fun unsubscribe(handler: IModelStoreChangeHandler<TModel>) {
        TODO("Not yet implemented")
    }
}