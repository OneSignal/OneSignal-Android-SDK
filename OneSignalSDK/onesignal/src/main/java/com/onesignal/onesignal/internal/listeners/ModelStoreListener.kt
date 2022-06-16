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

    enum class Action {
        CREATED,
        UPDATED,
        DELETED
    }

    // TODO: IApiService shouldn't be here, not a dependency of the listener but need to create the backend operations. Need a factory? Maybe factor method in IOperationRepo?

    init {
        store.subscribe(this)
    }

    override fun close() {
        store.unsubscribe(this)
    }

    override fun created(id: String, model: TModel) {
        val operation = getOperation(Action.CREATED, id, model)
        if(operation != null)
            opRepo.enqueue(operation)
    }

    override fun updated(id: String, before: TModel, after: TModel) {
        val operation = getOperation(Action.UPDATED, id, after)
        if(operation != null)
            opRepo.enqueue(operation)
    }

    override fun deleted(id: String, model: TModel) {
        val operation = getOperation(Action.DELETED, id, model)
        if(operation != null)
            opRepo.enqueue(operation)
    }

    abstract fun getOperation(action: Action, id: String, model: TModel) : Operation?
}
