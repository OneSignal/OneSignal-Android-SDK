package com.onesignal.core.internal.listeners

import com.onesignal.core.internal.modeling.ISingletonModelStore
import com.onesignal.core.internal.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.core.internal.modeling.Model
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.startup.IBootstrapService
import java.io.Closeable

internal abstract class SingletonModelStoreListener<TModel>(
    private val store: ISingletonModelStore<TModel>,
    private val opRepo: IOperationRepo
) : ISingletonModelStoreChangeHandler<TModel>, IBootstrapService, Closeable where TModel : Model {

    override fun bootstrap() {
        store.subscribe(this)
    }

    override fun close() {
        store.unsubscribe(this)
    }

    override fun onModelReplaced(model: TModel) {
        val operation = getReplaceOperation(model)
        if (operation != null) {
            opRepo.enqueue(operation)
        }
    }

    override fun onModelUpdated(model: TModel, path: String, property: String, oldValue: Any?, newValue: Any?) {
        val operation = getUpdateOperation(model, path, property, oldValue, newValue)
        if (operation != null) {
            opRepo.enqueue(operation)
        }
    }

    abstract fun getReplaceOperation(model: TModel): Operation?
    abstract fun getUpdateOperation(model: TModel, path: String, property: String, oldValue: Any?, newValue: Any?): Operation?
}
