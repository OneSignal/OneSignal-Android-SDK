package com.onesignal.common.modeling

import com.onesignal.common.events.EventProducer
import com.onesignal.common.events.IEventNotifier
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.internal.logging.Logging
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
    private val changeSubscription: EventProducer<IModelStoreChangeHandler<TModel>> = EventProducer()
    private val models: MutableList<TModel> = mutableListOf()
    private var hasLoadedFromCache = false

    override fun add(
        model: TModel,
        tag: String,
    ) {
        synchronized(models) {
            val oldModel = models.firstOrNull { it.id == model.id }
            if (oldModel != null) {
                removeItem(oldModel, tag)
            }

            addItem(model, tag)
        }
    }

    override fun add(
        index: Int,
        model: TModel,
        tag: String,
    ) {
        synchronized(models) {
            val oldModel = models.firstOrNull { it.id == model.id }
            if (oldModel != null) {
                removeItem(oldModel, tag)
            }

            addItem(model, tag, index)
        }
    }

    /**
     * @return list of read-only models, cloned for thread safety
     */
    override fun list(): Collection<TModel> {
        return synchronized(models) { models.toList() }
    }

    override fun get(id: String): TModel? {
        return models.firstOrNull { it.id == id }
    }

    override fun remove(
        id: String,
        tag: String,
    ) {
        synchronized(models) {
            val model = models.firstOrNull { it.id == id } ?: return
            removeItem(model, tag)
        }
    }

    override fun onChanged(
        args: ModelChangedArgs,
        tag: String,
    ) {
        persist()
        changeSubscription.fire { it.onModelUpdated(args, tag) }
    }

    override fun replaceAll(
        models: List<TModel>,
        tag: String,
    ) {
        synchronized(models) {
            clear(tag)

            for (model in models) {
                add(model, tag)
            }
        }
    }

    override fun clear(tag: String) {
        val localList = models.toList()
        synchronized(models) {
            models.clear()

            persist()
        }
        for (item in localList) {
            // no longer listen for changes to this model
            item.unsubscribe(this)
            changeSubscription.fire { it.onModelRemoved(item, tag) }
        }
    }

    private fun addItem(
        model: TModel,
        tag: String,
        index: Int? = null,
    ) {
        synchronized(models) {
            if (index != null) {
                models.add(index, model)
            } else {
                models.add(model)
            }

            // listen for changes to this model
            model.subscribe(this)

            persist()
        }
        changeSubscription.fire { it.onModelAdded(model, tag) }
    }

    private fun removeItem(
        model: TModel,
        tag: String,
    ) {
        synchronized(models) {
            models.remove(model)

            // no longer listen for changes to this model
            model.unsubscribe(this)

            persist()
        }
        changeSubscription.fire { it.onModelRemoved(model, tag) }
    }

    /**
     * When models are loaded from the cache, they are added to the front of existing models.
     * This is primarily to address operations which can enqueue before this method is called.
     */
    protected fun load() {
        if (name == null || _prefs == null) {
            return
        }

        val str = _prefs.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.MODEL_STORE_PREFIX + name, "[]")
        val jsonArray = JSONArray(str)

        val shouldRePersist = models.isNotEmpty()
        for (index in jsonArray.length() - 1 downTo 0) {
            val newModel = create(jsonArray.getJSONObject(index)) ?: continue

            /*
             * NOTE: Migration fix for bug introduced in 5.1.12
             * The following check is intended for the operation model store.
             * When the call to this method moved out of the operation model store's initializer,
             * duplicate operations could be cached.
             * See https://github.com/OneSignal/OneSignal-Android-SDK/pull/2099
             */
            val hasExisting = models.any { it.id == newModel.id }
            if (hasExisting) {
                Logging.debug("ModelStore<$name>: load - operation.id: ${newModel.id} already exists in the store.")
                continue
            }

            synchronized(models) {
                models.add(0, newModel)
            }
            // listen for changes to this model
            newModel.subscribe(this)
        }
        hasLoadedFromCache = true
        // optimization only: to avoid unnecessary writes
        if (shouldRePersist) {
            persist()
        }
    }

    /**
     * Any models added or changed before load is called are not persisted, to avoid overwriting the cache.
     * The time between any changes and loading from cache should be minuscule so lack of persistence is safe.
     * This is primarily to address operations which can enqueue before load() is called.
     */
    fun persist() {
        if (name == null || _prefs == null || !hasLoadedFromCache) {
            return
        }

        val jsonArray = JSONArray()
        synchronized(models) {
            for (model in models) {
                jsonArray.put(model.toJSON())
            }
        }

        _prefs.saveString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.MODEL_STORE_PREFIX + name, jsonArray.toString())
    }

    override fun subscribe(handler: IModelStoreChangeHandler<TModel>) = changeSubscription.subscribe(handler)

    override fun unsubscribe(handler: IModelStoreChangeHandler<TModel>) = changeSubscription.unsubscribe(handler)

    override val hasSubscribers: Boolean
        get() = changeSubscription.hasSubscribers
}
