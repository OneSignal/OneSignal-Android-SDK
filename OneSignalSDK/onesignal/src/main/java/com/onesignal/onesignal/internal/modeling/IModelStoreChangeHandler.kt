package com.onesignal.onesignal.internal.modeling

/**
 * A model store provides the ability to...store...a...model.
 */
interface IModelStoreChangeHandler<TModel> where TModel : Model {
    fun created(id: String, model: TModel)
    fun updated(id: String, before: TModel, after: TModel)
    fun deleted(id: String, model: TModel)
}