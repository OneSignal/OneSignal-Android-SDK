package com.onesignal.common.modeling

import com.onesignal.common.events.EventProducer
import com.onesignal.common.events.IEventNotifier
import com.onesignal.common.events.IEventProducer
import org.json.JSONArray
import org.json.JSONObject

/**
 * The base class for a [Model].  A model is effectively a map of data, each key in the map being
 * a property of the model, each value in the map being the property value.  A property can be
 * one of the following values:
 *
 * 1. A simple type.
 * 2. An instance of [Model] type.
 * 2. A [List] of simple types.
 * 3. A [List] of [Model] types.
 *
 * Simple Types
 * ------------
 * Boolean
 * String
 * Int/Long/Double (note deserialization does not always result in the same type i.e. Number)
 *
 * When a [Model] is nested (a property is a [Model] type or [List] of [Model] types) the child
 * [Model] is owned and initialized by the parent [Model].
 *
 * When a structured schema should be enforced this class should be extended, the base class
 * utilizing Properties with getters/setters that wrap [getProperty] and [setProperty] calls
 * to the underlying data.
 *
 * When a more dynamic schema is needed, the [MapModel] class can be used, which bridges a
 * [MutableMap] and [Model].
 *
 * Deserialization
 * ---------------
 * When deserializing a flat [Model] nothing specific is required.  However if the [Model]
 * is nested the [createModelForProperty] and/or [createListForProperty] needs to be implemented
 * to aide in the deserialization process.
 */
open class Model(
    /**
     * The optional parent model. When specified this model is a child model, any changes
     * to this model will *also* be propagated up to it's parent for notification. When
     * this is specified, must also specify [_parentProperty]
     */
    private val _parentModel: Model? = null,

    /**
     * The optional parent model property that references this model. When this is
     * specified, must also specify [_parentModel]
     */
    private val _parentProperty: String? = null

) : IEventNotifier<IModelChangedHandler> {

    /**
     * A unique identifier for this model.
     */
    var id: String
        get() = getProperty(::id.name)
        set(value) { setProperty(::id.name, value) }

    protected val data: MutableMap<String, Any?> = mutableMapOf()
    private val _changeNotifier: IEventProducer<IModelChangedHandler> = EventProducer()

    init {
        if (_parentModel != null && _parentProperty == null) {
            throw Exception("If parent model is set, parent property must also be set.")
        } else if (_parentModel == null && _parentProperty != null) {
            throw Exception("If parent property is set, parent model must also be set.")
        }
    }

    /**
     * Initialize this model from a [JSONObject].  Each key-value-pair in the [JSONObject]
     * will be deserialized into this model, recursively if needed.
     *
     * @param jsonObject The [JSONObject] to initialize this model from.
     */
    fun initializeFromJson(jsonObject: JSONObject) {
        data.clear()
        for (property in jsonObject.keys()) {
            val jsonValue = jsonObject.get(property)
            if (jsonValue is JSONObject) {
                val childModel = createModelForProperty(property, jsonValue)
                if (childModel != null) {
                    data[property] = childModel
                }
            } else if (jsonValue is JSONArray) {
                val listOfItems = createListForProperty(property, jsonValue)
                if (listOfItems != null) {
                    data[property] = listOfItems
                }
            } else {
                data[property] = jsonObject.get(property)
            }
        }
    }

    /**
     * Initialize this model from a [Model].  The model provided will be replicated
     * within this model.
     *
     * @param id The id of the model to initialze to.
     * @param model The model to initialize this model from.
     */
    fun initializeFromModel(id: String, model: Model) {
        data.clear()
        data.putAll(model.data)
        data[::id.name] = id
    }

    /**
     * Called via [initializeFromJson] when the property being initialized is a [JSONObject],
     * indicating the property value should be set to a nested [Model].  The specific concrete
     * class of [Model] for this property is determined by the implementor and should depend on
     * the [property] provided.
     *
     * @param property The property that is to contain the [Model] created by this method.
     * @param jsonObject The [JSONObject] that the [Model] will be created/initialized from.
     *
     * @return The created [Model], or null if the property should not be set.
     */
    protected open fun createModelForProperty(property: String, jsonObject: JSONObject): Model? = null

    /**
     * Called via [initializeFromJson] when the property being initialized is a [JSONArray],
     * indicating the property value should be set to a [List].  The specific concrete class
     * inside the [List] for this property is determined by the implementor and should depend
     * on the [property] provided.
     *
     * @param property The property that is to contain the [List] created by this method.
     * @param jsonArray The [JSONArray] that the [List] will be created/initialized from.
     *
     * @return The created [List], or null if the property should not be set.
     */
    protected open fun createListForProperty(property: String, jsonArray: JSONArray): List<*>? = null

    /**
     * Set a property on this model to the provided value, with the ability to prevent
     * firing the notification of the change.
     *
     * Note, this should not be used directly unless you know what you're doing.
     *
     * @param name: The name of the property that is to be set.
     * @param value: The value of the property to set it to.
     * @param tag The tag which identifies how/why the property is being set.
     */
    fun <T> setProperty(name: String, value: T, tag: String = ModelChangeTags.NORMAL) {
        val oldValue = data[name]
        val newValue = value as Any?

        if (oldValue == newValue) {
            return
        }

        if (newValue != null) {
            data[name] = newValue
        } else if (data.containsKey(name)) {
            data.remove(name)
        }

        notifyChanged(name, name, tag, oldValue, newValue)
    }

    /**
     * Determine whether the provided property is currently set in this model.
     *
     * @param name The name of the property to test for.
     *
     * @return True if this model has the provided property, false otherwise.
     */
    fun hasProperty(name: String): Boolean = data.containsKey(name)

    /**
     * Retrieve the property on this model with the provided name.
     *
     * @param name: The name of the property that is to be retrieved.
     * @param create: An optional lambda to provide which will be called to create a default
     * value for the property, in the event it doesn't exist yet.
     *
     * @return The value for this property.
     */
    fun <T> getProperty(name: String, create: (() -> T)? = null): T {
        return if (data.containsKey(name)) {
            data[name] as T
        } else if (create != null) {
            val defaultValue = create()
            data[name] = defaultValue as Any?
            defaultValue
        } else {
            data[name] as T
        }
    }

    private fun notifyChanged(path: String, property: String, tag: String, oldValue: Any?, newValue: Any?) {
        // if there are any changed listeners for this specific model, notify them.
        val changeArgs = ModelChangedArgs(this, path, property, oldValue, newValue)
        _changeNotifier.fire { it.onChanged(changeArgs, tag) }

        // if there is a parent model, propagate the change up to the parent for it's own processing.
        if (_parentModel != null) {
            val parentPath = "$_parentProperty.$path"
            _parentModel.notifyChanged(parentPath, property, tag, oldValue, newValue)
        }
    }

    /**
     * Serialize this model to a [JSONObject], recursively if required.
     *
     * @return The resulting [JSONObject].
     */
    fun toJSON(): JSONObject {
        val jsonObject = JSONObject()
        for (kvp in data) {
            when (val value = kvp.value) {
                is Model -> {
                    jsonObject.put(kvp.key, value.toJSON())
                }
                is List<*> -> {
                    val jsonArray = JSONArray()
                    for (arrayItem in value) {
                        if (arrayItem is Model) {
                            jsonArray.put(arrayItem.toJSON())
                        } else {
                            jsonArray.put(arrayItem)
                        }
                    }
                    jsonObject.put(kvp.key, jsonArray)
                }
                else -> {
                    jsonObject.put(kvp.key, value)
                }
            }
        }
        return jsonObject
    }

    override fun subscribe(handler: IModelChangedHandler) = _changeNotifier.subscribe(handler)
    override fun unsubscribe(handler: IModelChangedHandler) = _changeNotifier.unsubscribe(handler)
}
