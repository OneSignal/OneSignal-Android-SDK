package com.onesignal.models.modeling

import com.onesignal.onesignal.internal.modeling.Model

/**
 * A model store that is implemented in memory.
 */
internal class CachingModelStore<TModel>(
        val cache: IModelStore<TModel>,
        val persist: IModelStore<TModel>
        ) : IModelStore<TModel> where TModel : Model {

    override fun create(id: String, model: TModel) {
        cache.create(id, model)
        persist.create(id, model)
    }

    override fun list() : Collection<TModel> {
        return persist.list()
    }

    override fun get(id: String) : TModel? {
        var item = cache.get(id)
        if(item != null)
            return item

        // pull from persist, caching if retrieved
        item = persist.get(id)
        if(item != null)
            cache.create(id, item)

        return item
    }

    override fun update(id: String, model: TModel) {
        cache.update(id, model)
        persist.update(id, model)
    }

    override fun delete(id: String) {
        cache.delete(id)
        persist.delete(id)
    }
}