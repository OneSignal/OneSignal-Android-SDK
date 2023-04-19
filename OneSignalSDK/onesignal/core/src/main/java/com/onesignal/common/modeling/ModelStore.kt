package com.onesignal.common.modeling

import com.onesignal.common.events.EventProducer
import com.onesignal.common.events.IEventNotifier
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
abstract class ModelStore<TModel>(
    /**
     * The persistable name of the model store. If not specified no persisting will occur.
     */
    val name: String? = null,
    private val _prefs: IPreferencesService? = null,
) : IEventNotifier<IModelStoreChangeHandler<TModel>>,
    IModelStore<TModel>,
    IModelChangedHandler where TModel : Model {

    private val _changeSubscription: EventProducer<IModelStoreChangeHandler<TModel>> = EventProducer()
    private val _models: MutableList<TModel> = mutableListOf()

    override fun add(model: TModel, tag: String) {
        val oldModel = _models.firstOrNull { it.id == model.id }
        if (oldModel != null) {
            removeItem(oldModel, tag)
        }

        addItem(model, tag)
    }

    override fun add(index: Int, model: TModel, tag: String) {
        val oldModel = _models.firstOrNull { it.id == model.id }
        if (oldModel != null) {
            removeItem(oldModel, tag)
        }

        addItem(model, tag, index)
    }

    override fun list(): Collection<TModel> {
        return _models
    }

    override fun get(id: String): TModel? {
        return _models.firstOrNull { it.id == id }
    }

    override fun remove(id: String, tag: String) {
        val model = _models.firstOrNull { it.id == id } ?: return
        removeItem(model, tag)
    }

    override fun onChanged(args: ModelChangedArgs, tag: String) {
        persist()

        _changeSubscription.fire { it.onModelUpdated(args, tag) }
    }

    override fun replaceAll(models: List<TModel>, tag: String) {
        clear(tag)

        for (model in models) {
            add(model, tag)
        }
    }

    override fun clear(tag: String) {
        val localList = _models.toList()
        _models.clear()

        persist()

        for (item in localList) {
            // no longer listen for changes to this model
            item.unsubscribe(this)
            _changeSubscription.fire { it.onModelRemoved(item, tag) }
        }
    }

    private fun addItem(model: TModel, tag: String, index: Int? = null) {
        if (index != null) {
            _models.add(index, model)
        } else {
            _models.add(model)
        }

        // listen for changes to this model
        model.subscribe(this)

        persist()

        _changeSubscription.fire { it.onModelAdded(model, tag) }
    }

    private fun removeItem(model: TModel, tag: String) {
        _models.remove(model)

        // no longer listen for changes to this model
        model.unsubscribe(this)

        persist()

        _changeSubscription.fire { it.onModelRemoved(model, tag) }
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
    override val hasSubscribers: Boolean
        get() = _changeSubscription.hasSubscribers
}
