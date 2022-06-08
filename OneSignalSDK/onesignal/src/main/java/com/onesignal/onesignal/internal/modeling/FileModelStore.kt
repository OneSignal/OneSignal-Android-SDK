package com.onesignal.models.modeling

import com.onesignal.onesignal.internal.modeling.Model

/**
 * A model store that is implemented in memory.
 */
internal class FileModelStore<TModel> : IModelStore<TModel> where TModel : Model {
    override fun create(id: String, model: TModel) {
        // TODO: Implement
    }

    override fun list() : Collection<TModel> {
        // TODO: Implement
        return ArrayList<TModel>()
    }

    override fun get(id: String) : TModel? {
        // TODO: Implement
        return null;
    }

    override fun update(id: String, model: TModel) {
        // TODO: Implement
    }

    override fun delete(id: String) {
        // TODO: Implement
    }
}