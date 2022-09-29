package com.onesignal.core.internal.modeling

import com.onesignal.core.internal.common.events.EventProducer
import com.onesignal.core.internal.common.events.IEventNotifier

/**
 * The implementation of a model store.
 */
internal open class ModelStore<TModel>(
    /**
     * Whether the model store should be persisted to disk (the model lives across app lifecycles).
     * Defaults to `true`.
     */
    val persist: Boolean = true,
    private val _changeSubscription: EventProducer<IModelStoreChangeHandler<TModel>> = EventProducer()
) : IEventNotifier<IModelStoreChangeHandler<TModel>> by _changeSubscription, IModelStore<TModel>, IModelChangedHandler where TModel : Model {

    private val _models: MutableList<TModel> = mutableListOf()

    init {
        // TODO: Load the persisted models
    }

    override fun add(model: TModel, fireEvent: Boolean) {
        val oldModel = _models.firstOrNull { it.id == model.id }
        if (oldModel != null) {
            removeItem(oldModel, fireEvent)
        }

        addItem(model, fireEvent)
    }

    override fun list(): Collection<TModel> {
        return _models
    }

    override fun get(id: String): TModel? {
        return _models.firstOrNull { it.id == id }
    }

    override fun remove(id: String, fireEvent: Boolean) {
        val model = _models.firstOrNull { it.id == id } ?: return
        removeItem(model, fireEvent)
    }

    override fun onChanged(args: ModelChangedArgs) {
        // TODO: Persist the changed model to storage. Consider batching.

        _changeSubscription.fire { it.onUpdated(args.model as TModel, args.property, args.oldValue, args.newValue) }
    }

    override fun replaceAll(models: List<TModel>, fireEvent: Boolean) {
        clear(fireEvent)

        for (model in models) {
            add(model, fireEvent)
        }
    }

    override fun clear(fireEvent: Boolean) {
        val localList = _models.toList()
        _models.clear()

        for (item in localList) {
            removeItem(item, fireEvent)
        }
    }

    private fun addItem(model: TModel, fireEvent: Boolean) {
        _models.add(model)

        // listen for changes to this model
        model.subscribe(this)

        if (persist) {
            // TODO: Persist the new model to storage.
        }

        if (fireEvent) {
            _changeSubscription.fire { it.onAdded(model) }
        }
    }

    private fun removeItem(model: TModel, fireEvent: Boolean) {
        _models.remove(model)

        // no longer listen for changes to this model
        model.unsubscribe(this)

        if (persist) {
            // TODO: Remove the model from storage
        }

        if (fireEvent) {
            _changeSubscription.fire { it.onRemoved(model) }
        }
    }
}
