package com.onesignal.core.internal.operations.listeners

import com.onesignal.common.modeling.IModelStore
import com.onesignal.common.modeling.IModelStoreChangeHandler
import com.onesignal.common.modeling.Model
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.startup.IBootstrapService
import java.io.Closeable

/**
 * A [IModelStore] listener that will translate a change to the model/model store to an
 * operation that is to be enqueued onto the [IOperationRepo]. This is an abstract class,
 * a concrete implementation must provide the actual [Operation] that should be enqueued.
 */
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

    /**
     * Called when a model has been added to the model store.
     *
     * @return The operation to enqueue when a model has been added, or null if no operation should be enqueued.
     */
    abstract fun getAddOperation(model: TModel): Operation?

    /**
     * Called when a model has been removed from the model store.
     *
     * @return The operation to enqueue when a model has been removed, or null if no operation should be enqueued.
     */
    abstract fun getRemoveOperation(model: TModel): Operation?

    /**
     * Called when the model has been updated.
     *
     * @return The operation to enqueue when the model has been updated, or null if no operation should be enqueued.
     */
    abstract fun getUpdateOperation(model: TModel, path: String, property: String, oldValue: Any?, newValue: Any?): Operation?
}
