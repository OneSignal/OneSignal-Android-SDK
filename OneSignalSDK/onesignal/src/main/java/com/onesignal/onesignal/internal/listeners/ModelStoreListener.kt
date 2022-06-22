package com.onesignal.onesignal.internal.listeners

import com.onesignal.onesignal.internal.backend.api.IApiService
import com.onesignal.onesignal.internal.modeling.IModelStore
import com.onesignal.onesignal.internal.modeling.IModelStoreChangeHandler
import com.onesignal.onesignal.internal.modeling.Model
import com.onesignal.onesignal.internal.operations.IOperationRepo
import com.onesignal.onesignal.internal.operations.Operation
import java.io.Closeable

abstract class ModelStoreListener<TModel>(
    private val store: IModelStore<TModel>,
    private val opRepo: IOperationRepo,
    protected val api: IApiService) : IModelStoreChangeHandler<TModel>, Closeable where TModel : Model {

    // TODO: IApiService shouldn't be here, not a dependency of the listener but need to create the backend operations. Need a factory? Maybe factor method in IOperationRepo?

    init {
        store.subscribe(this)
    }

    override fun close() {
        store.unsubscribe(this)
    }

    override fun added(model: TModel) {
        val operation = getAddOperation(model)
        if(operation != null)
            opRepo.enqueue(operation)
    }

    override fun updated(model: TModel, property: String, oldValue: Any?, newValue: Any?) {
        val operation = getUpdateOperation(model, property, oldValue, newValue)
        if(operation != null)
            opRepo.enqueue(operation)
    }

    override fun removed(model: TModel) {
        val operation = getRemoveOperation(model)
        if(operation != null)
            opRepo.enqueue(operation)
    }

    abstract fun getAddOperation(model: TModel) : Operation?
    abstract fun getRemoveOperation(model: TModel) : Operation?
    abstract fun getUpdateOperation(model: TModel, property: String, oldValue: Any?, newValue: Any?) : Operation?
}
