package com.onesignal.onesignal.core.internal.listeners

import com.onesignal.onesignal.core.internal.modeling.IModelStore
import com.onesignal.onesignal.core.internal.modeling.IModelStoreChangeHandler
import com.onesignal.onesignal.core.internal.modeling.Model
import com.onesignal.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.onesignal.core.internal.operations.Operation
import com.onesignal.onesignal.core.internal.startup.IStartableService
import java.io.Closeable

abstract class ModelStoreListener<TModel>(
    private val store: IModelStore<TModel>,
    private val opRepo: IOperationRepo
) : IModelStoreChangeHandler<TModel>, IStartableService, Closeable where TModel : Model {

    override fun start() {
        store.subscribe(this)
    }

    override fun close() {
        store.unsubscribe(this)
    }

    override fun onAdded(model: TModel) {
        val operation = getAddOperation(model)
        if (operation != null)
            opRepo.enqueue(operation)
    }

    override fun onUpdated(model: TModel, property: String, oldValue: Any?, newValue: Any?) {
        val operation = getUpdateOperation(model, property, oldValue, newValue)
        if (operation != null)
            opRepo.enqueue(operation)
    }

    override fun onRemoved(model: TModel) {
        val operation = getRemoveOperation(model)
        if (operation != null)
            opRepo.enqueue(operation)
    }

    abstract fun getAddOperation(model: TModel): Operation?
    abstract fun getRemoveOperation(model: TModel): Operation?
    abstract fun getUpdateOperation(model: TModel, property: String, oldValue: Any?, newValue: Any?): Operation?
}
