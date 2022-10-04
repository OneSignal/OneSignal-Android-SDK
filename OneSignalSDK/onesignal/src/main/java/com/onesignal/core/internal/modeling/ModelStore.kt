package com.onesignal.core.internal.modeling

import com.onesignal.core.internal.common.events.EventProducer
import com.onesignal.core.internal.common.events.IEventNotifier
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import org.json.JSONArray

/**
 * The abstract implementation of a model store.  Implements all but the [create] method,
 * which must be implemented by the concrete class as this abstract implementation is not
 * able to derive and instantiate the concrete model type being stored.
 *
 * Persistence
 * -----------
 * When persistence is enabled for this model store (i.e. a [name] and [_prefs] parameters
 * are provided on initialization) any [Model] that has been added to the model store will
 * be persisted, including any update to that model, when that property update drives a
 * [IModelChangedHandler.onChanged] event. If a model property update does *not* drive
 * a [IModelChangedHandler.onChanged] event but persistence is still desired, there is a
 * [persist] method that can be called at any time.
 *
 * Instantiating this model store with persistence will load any previously persisted models
 * as part of its initialization process.
 */
internal abstract class ModelStore<TModel>(
    /**
     * The persistable name of the model store. If not specified no persisting will occur.
     */
    val name: String? = null,
    private val _prefs: IPreferencesService? = null
) : IEventNotifier<IModelStoreChangeHandler<TModel>>, IModelStore<TModel>, IModelChangedHandler where TModel : Model {

    private val _changeSubscription: EventProducer<IModelStoreChangeHandler<TModel>> = EventProducer()
    private val _models: MutableList<TModel> = mutableListOf()

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
        persist()
        _changeSubscription.fire { it.onUpdated(args.model as TModel, args.path, args.property, args.oldValue, args.newValue) }
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

        persist()

        if (fireEvent) {
            _changeSubscription.fire { it.onAdded(model) }
        }
    }

    private fun removeItem(model: TModel, fireEvent: Boolean) {
        _models.remove(model)

        // no longer listen for changes to this model
        model.unsubscribe(this)

        persist()

        if (fireEvent) {
            _changeSubscription.fire { it.onRemoved(model) }
        }
    }

    protected fun load() {
        if (name != null && _prefs != null) {
            val str = _prefs.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.MODEL_STORE_PREFIX + name, "[]")
            val jsonArray = JSONArray(str)
            for (index in 0 until jsonArray.length()) {
                val newModel = create(jsonArray.getJSONObject(index))
                _models.add(newModel)
                // listen for changes to this model
                newModel.subscribe(this)
            }
        }
    }

    /**
     * Persist this model store.
     */
    fun persist() {
        if (name != null && _prefs != null) {
            val jsonArray = JSONArray()
            for (model in _models) {
                jsonArray.put(model.toJSON())
            }

            _prefs.saveString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.MODEL_STORE_PREFIX + name, jsonArray.toString())
        }
    }

    override fun subscribe(handler: IModelStoreChangeHandler<TModel>) = _changeSubscription.subscribe(handler)
    override fun unsubscribe(handler: IModelStoreChangeHandler<TModel>) = _changeSubscription.unsubscribe(handler)
}
