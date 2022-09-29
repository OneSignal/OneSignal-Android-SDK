package com.onesignal.core.internal.modeling

import com.onesignal.core.internal.preferences.IPreferencesService
import org.json.JSONObject

/**
 * A simple model store is a concrete implementation of the [ModelStore] which provides
 * a basic implementation of the [create] method.  Specifically, it will instantiate a
 * new instance of the [TModel] via the lambda [_create] passed in during initialization.
 */
internal open class SimpleModelStore<TModel>(
    /**
     * Will be called whenever a new [TModel] needs to be instantiated.
     */
    private val _create: () -> TModel,

    /**
     * The persistable name of the model store. If not specified no persisting will occur.
     */
    name: String? = null,
    _prefs: IPreferencesService? = null
) : ModelStore<TModel>(name, _prefs) where TModel : Model {

    init {
        load()
    }

    override fun create(jsonObject: JSONObject?): TModel {
        val model = _create()

        if (jsonObject != null) {
            model.initializeFromJson(jsonObject)
        }

        return model
    }
}
