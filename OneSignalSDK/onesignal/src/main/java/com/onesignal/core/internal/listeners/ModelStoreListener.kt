package com.onesignal.core.internal.listeners

import com.onesignal.core.internal.modeling.IModelStore
import com.onesignal.core.internal.modeling.IModelStoreChangeHandler
import com.onesignal.core.internal.modeling.Model
import com.onesignal.core.internal.modeling.ModelChangeTags
import com.onesignal.core.internal.modeling.ModelChangedArgs
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.startup.IBootstrapService
import java.io.Closeable

internal abstract class ModelStoreListener<TModel>(
    private val store: IModelStore<TModel>,
    private val opRepo: IOperationRepo
) : IModelStoreChangeHandler<TModel>, IBootstrapService, Closeable where TModel : Model {

    override fun bootstrap() {
        store.subscribe(this)
    }

    override fun close() {
        store.unsubscribe(this)
    }

    override fun onModelAdded(model: TModel, tag: String) {
        if (tag != ModelChangeTags.NORMAL) {
            return
        }

        val operation = getAddOperation(model)
        if (operation != null) {
            opRepo.enqueue(operation)
        }
    }

    override fun onModelUpdated(args: ModelChangedArgs, tag: String) {
        if (tag != ModelChangeTags.NORMAL) {
            return
        }

        val operation = getUpdateOperation(args.model as TModel, args.path, args.property, args.oldValue, args.newValue)
        if (operation != null) {
            opRepo.enqueue(operation)
        }
    }

    override fun onModelRemoved(model: TModel, tag: String) {
        if (tag != ModelChangeTags.NORMAL) {
            return
        }

        val operation = getRemoveOperation(model)
        if (operation != null) {
            opRepo.enqueue(operation)
        }
    }

    abstract fun getAddOperation(model: TModel): Operation?
    abstract fun getRemoveOperation(model: TModel): Operation?
    abstract fun getUpdateOperation(model: TModel, path: String, property: String, oldValue: Any?, newValue: Any?): Operation?
}
