package com.onesignal.core.internal.operations.listeners

import com.onesignal.common.modeling.ISingletonModelStore
import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.Model
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.common.modeling.SingletonModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.startup.IBootstrapService
import java.io.Closeable

/**
 * A [SingletonModelStore] listener that will translate a change to the model in the
 * singleton store to an operation that is to be enqueued onto the [IOperationRepo].
 * This is an abstract class, a concrete implementation must provide the actual
 * [Operation] that should be enqueued.
 */
internal abstract class SingletonModelStoreListener<TModel>(
    private val store: ISingletonModelStore<TModel>,
    private val opRepo: IOperationRepo,
) : ISingletonModelStoreChangeHandler<TModel>, IBootstrapService, Closeable where TModel : Model {

    override fun bootstrap() {
        store.subscribe(this)
    }

    override fun close() {
        store.unsubscribe(this)
    }

    override fun onModelReplaced(model: TModel, tag: String) {
        if (tag != ModelChangeTags.NORMAL) {
            return
        }

        val operation = getReplaceOperation(model)
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

    /**
     * Called when the model has been replaced.
     *
     * @return The operation to enqueue when the model has been replaced, or null if no operation should be enqueued.
     */
    abstract fun getReplaceOperation(model: TModel): Operation?

    /**
     * Called when the model has been updated.
     *
     * @return The operation to enqueue when the model has been updated, or null if no operation should be enqueued.
     */
    abstract fun getUpdateOperation(model: TModel, path: String, property: String, oldValue: Any?, newValue: Any?): Operation?
}
