package com.onesignal.models.modeling

/**
 * A model store provides the ability to...store...a...model.
 */
interface IModelStore<TModel> where TModel : Model {
    fun create(id: String, model: TModel)
    fun list() : Collection<TModel>
    fun get(id: String) : TModel?
    fun update(id: String, model: TModel)
    fun delete(id: String)
}

/**
 * A model store that expects to only store a single instance
 * of a model.
 */
interface ISingletonModelStore<TModel> where TModel : Model {
    fun create(model: TModel)
    fun get() : TModel?
    fun update(model: TModel)
    fun delete()
}