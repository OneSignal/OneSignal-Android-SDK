package com.onesignal.common.modeling

import com.onesignal.common.events.EventProducer
import com.onesignal.common.events.IEventNotifier
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.Collections

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
 * Int/Long/Double/Float
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
    private var _parentModel: Model? = null,
    /**
     * The optional parent model property that references this model. When this is
     * specified, must also specify [_parentModel]
     */
    private val _parentProperty: String? = null,
) : IEventNotifier<IModelChangedHandler> {
    /**
     * A unique identifier for this model.
     */
    var id: String
        get() = getStringProperty(::id.name)
        set(value) {
            setStringProperty(::id.name, value)
        }

    protected val data: MutableMap<String, Any?> = Collections.synchronizedMap(mutableMapOf())
    private val changeNotifier = EventProducer<IModelChangedHandler>()

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
        synchronized(data) {
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
                    // Cast JSON value based on class's property getter name return type
                    val method =
                        this.javaClass.methods.firstOrNull {
                            it.returnType != Void::class.java &&
                                it.parameterCount == 0 &&
                                it.name.equals(
                                    "get$property",
                                    true,
                                )
                        }

                    when (method?.returnType) {
                        Double::class.java, java.lang.Double::class.java -> data[property] = jsonObject.getDouble(property)
                        Long::class.java, java.lang.Long::class.java -> data[property] = jsonObject.getLong(property)
                        Float::class.java, java.lang.Float::class.java -> data[property] = jsonObject.getDouble(property).toFloat()
                        Int::class.java, java.lang.Integer::class.java -> data[property] = jsonObject.getInt(property)
                        Boolean::class.java, java.lang.Boolean::class.java -> data[property] = jsonObject.getBoolean(property)
                        String::class.java, java.lang.String::class.java -> data[property] = jsonObject.getString(property)
                        else -> data[property] = jsonObject.get(property)
                    }
                }
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
    fun initializeFromModel(
        id: String?,
        model: Model,
    ) {
        val newData = Collections.synchronizedMap(mutableMapOf<String, Any?>())

        for (item in model.data) {
            if (item.value is Model) {
                val childModel = item.value as Model
                childModel._parentModel = this
                newData[item.key] = childModel
            } else {
                newData[item.key] = item.value
            }
        }

        if (id != null) {
            newData[::id.name] = id
        }

        synchronized(data) {
            data.clear()
            data.putAll(newData)
        }
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
    protected open fun createModelForProperty(
        property: String,
        jsonObject: JSONObject,
    ): Model? = null

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
    protected open fun createListForProperty(
        property: String,
        jsonArray: JSONArray,
    ): List<*>? = null

    inline fun <reified T : Enum<T>> setEnumProperty(
        name: String,
        value: T,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptEnumProperty(
        name,
        value,
        tag,
        forceChange,
    )

    fun <T> setMapModelProperty(
        name: String,
        value: MapModel<T>,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptMapModelProperty(
        name,
        value,
        tag,
        forceChange,
    )

    fun <T> setListProperty(
        name: String,
        value: List<T>,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptListProperty(
        name,
        value,
        tag,
        forceChange,
    )

    fun setStringProperty(
        name: String,
        value: String,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptStringProperty(
        name,
        value,
        tag,
        forceChange,
    )

    fun setBooleanProperty(
        name: String,
        value: Boolean,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptBooleanProperty(
        name,
        value,
        tag,
        forceChange,
    )

    fun setLongProperty(
        name: String,
        value: Long,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptLongProperty(
        name,
        value,
        tag,
        forceChange,
    )

    fun setDoubleProperty(
        name: String,
        value: Double,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptDoubleProperty(
        name,
        value,
        tag,
        forceChange,
    )

    fun setFloatProperty(
        name: String,
        value: Float,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptFloatProperty(
        name,
        value,
        tag,
        forceChange,
    )

    fun setIntProperty(
        name: String,
        value: Int,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptIntProperty(
        name,
        value,
        tag,
        forceChange,
    )

    fun setBigDecimalProperty(
        name: String,
        value: BigDecimal,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptBigDecimalProperty(
        name,
        value,
        tag,
        forceChange,
    )

    fun setAnyProperty(
        name: String,
        value: Any,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptAnyProperty(
        name,
        value,
        tag,
        forceChange,
    )

    inline fun <reified T : Enum<T>> setOptEnumProperty(
        name: String,
        value: T?,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptAnyProperty(
        name,
        value?.toString(),
        tag,
        forceChange,
    )

    fun <T> setOptMapModelProperty(
        name: String,
        value: MapModel<T>?,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptAnyProperty(
        name,
        value,
        tag,
        forceChange,
    )

    fun <T> setOptListProperty(
        name: String,
        value: List<T>?,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptAnyProperty(
        name,
        value,
        tag,
        forceChange,
    )

    fun setOptStringProperty(
        name: String,
        value: String?,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptAnyProperty(
        name,
        value,
        tag,
        forceChange,
    )

    fun setOptBooleanProperty(
        name: String,
        value: Boolean?,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptAnyProperty(
        name,
        value,
        tag,
        forceChange,
    )

    fun setOptLongProperty(
        name: String,
        value: Long?,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptAnyProperty(
        name,
        value,
        tag,
        forceChange,
    )

    fun setOptDoubleProperty(
        name: String,
        value: Double?,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptAnyProperty(
        name,
        value,
        tag,
        forceChange,
    )

    fun setOptFloatProperty(
        name: String,
        value: Float?,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptAnyProperty(
        name,
        value,
        tag,
        forceChange,
    )

    fun setOptIntProperty(
        name: String,
        value: Int?,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptAnyProperty(
        name,
        value,
        tag,
        forceChange,
    )

    fun setOptBigDecimalProperty(
        name: String,
        value: BigDecimal?,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) = setOptAnyProperty(
        name,
        value?.toString(),
        tag,
        forceChange,
    )

    fun setOptAnyProperty(
        name: String,
        value: Any?,
        tag: String = ModelChangeTags.NORMAL,
        forceChange: Boolean = false,
    ) {
        val oldValue = data[name]
        synchronized(data) {
            if (oldValue == value && !forceChange) {
                return
            }

            if (value != null) {
                data[name] = value
            } else if (data.containsKey(name)) {
                data.remove(name)
            }
        }
        notifyChanged(name, name, tag, oldValue, value)
    }

    /**
     * Determine whether the provided property is currently set in this model.
     *
     * @param name The name of the property to test for.
     *
     * @return True if this model has the provided property, false otherwise.
     */
    fun hasProperty(name: String): Boolean = data.containsKey(name)

    protected inline fun <reified T : Enum<T>> getEnumProperty(name: String): T = getOptEnumProperty<T>(name) as T

    protected fun <T> getMapModelProperty(
        name: String,
        create: (() -> MapModel<T>)? = null,
    ): MapModel<T> =
        getOptMapModelProperty(
            name,
            create,
        ) as MapModel<T>

    protected fun <T> getListProperty(
        name: String,
        create: (() -> List<T>)? = null,
    ): List<T> = getOptListProperty(name, create) as List<T>

    protected fun getStringProperty(
        name: String,
        create: (() -> String)? = null,
    ): String = getOptStringProperty(name, create) as String

    protected fun getBooleanProperty(
        name: String,
        create: (() -> Boolean)? = null,
    ): Boolean =
        getOptBooleanProperty(
            name,
            create,
        ) as Boolean

    protected fun getLongProperty(
        name: String,
        create: (() -> Long)? = null,
    ): Long = getOptLongProperty(name, create) as Long

    protected fun getDoubleProperty(
        name: String,
        create: (() -> Double)? = null,
    ): Double = getOptDoubleProperty(name, create) as Double

    protected fun getFloatProperty(
        name: String,
        create: (() -> Float)? = null,
    ): Float = getOptFloatProperty(name, create) as Float

    protected fun getIntProperty(
        name: String,
        create: (() -> Int)? = null,
    ): Int = getOptIntProperty(name, create) as Int

    protected fun getBigDecimalProperty(
        name: String,
        create: (() -> BigDecimal)? = null,
    ): BigDecimal =
        getOptBigDecimalProperty(
            name,
            create,
        ) as BigDecimal

    protected fun getAnyProperty(
        name: String,
        create: (() -> Any)? = null,
    ): Any = getOptAnyProperty(name, create) as Any

    protected inline fun <reified T : Enum<T>> getOptEnumProperty(name: String): T? {
        val value = getOptAnyProperty(name) ?: return null

        if (value is T) return value
        if (value is String) return enumValueOf<T>(value)
        return value as T
    }

    protected fun <T> getOptMapModelProperty(
        name: String,
        create: (() -> MapModel<T>?)? = null,
    ): MapModel<T>? =
        getOptAnyProperty(
            name,
            create,
        ) as MapModel<T>?

    protected fun <T> getOptListProperty(
        name: String,
        create: (() -> List<T>?)? = null,
    ): List<T>? =
        getOptAnyProperty(
            name,
            create,
        ) as List<T>?

    protected fun getOptStringProperty(
        name: String,
        create: (() -> String?)? = null,
    ): String? = getOptAnyProperty(name, create) as String?

    protected fun getOptBooleanProperty(
        name: String,
        create: (() -> Boolean?)? = null,
    ): Boolean? =
        getOptAnyProperty(
            name,
            create,
        ) as Boolean?

    protected fun getOptLongProperty(
        name: String,
        create: (() -> Long?)? = null,
    ): Long? {
        val value = getOptAnyProperty(name, create) ?: return null

        if (value is Long) return value
        if (value is Int) return value.toLong()
        if (value is Float) return value.toLong()
        if (value is Double) return value.toLong()
        return value as Long?
    }

    protected fun getOptFloatProperty(
        name: String,
        create: (() -> Float?)? = null,
    ): Float? {
        val value = getOptAnyProperty(name, create) ?: return null

        if (value is Float) return value
        if (value is Double) return value.toFloat()
        if (value is Int) return value.toFloat()
        if (value is Long) return value.toFloat()
        return value as Float?
    }

    protected fun getOptDoubleProperty(
        name: String,
        create: (() -> Double?)? = null,
    ): Double? {
        val value = getOptAnyProperty(name, create) ?: return null

        if (value is Double) return value
        if (value is Float) return value.toDouble()
        if (value is Int) return value.toDouble()
        if (value is Long) return value.toDouble()
        return value as Double?
    }

    protected fun getOptIntProperty(
        name: String,
        create: (() -> Int?)? = null,
    ): Int? {
        val value = getOptAnyProperty(name, create) ?: return null

        if (value is Int) return value
        if (value is Long) return value.toInt()
        if (value is Float) return value.toInt()
        if (value is Double) return value.toInt()
        return value as Int?
    }

    protected fun getOptBigDecimalProperty(
        name: String,
        create: (() -> BigDecimal?)? = null,
    ): BigDecimal? {
        val value = getOptAnyProperty(name, create) ?: return null

        if (value is Int) return BigDecimal(value)
        if (value is Long) return BigDecimal(value)
        if (value is Float) return BigDecimal(value.toDouble())
        if (value is Double) return BigDecimal(value)
        if (value is String) return BigDecimal(value)
        return value as BigDecimal?
    }

    protected fun getOptAnyProperty(
        name: String,
        create: (() -> Any?)? = null,
    ): Any? {
        synchronized(data) {
            return if (data.containsKey(name) || create == null) {
                data[name]
            } else {
                val defaultValue = create()
                data[name] = defaultValue as Any?
                defaultValue
            }
        }
    }

    private fun notifyChanged(
        path: String,
        property: String,
        tag: String,
        oldValue: Any?,
        newValue: Any?,
    ) {
        // if there are any changed listeners for this specific model, notify them.
        val changeArgs = ModelChangedArgs(this, path, property, oldValue, newValue)
        changeNotifier.fire { it.onChanged(changeArgs, tag) }

        // if there is a parent model, propagate the change up to the parent for it's own processing.
        if (_parentModel != null) {
            val parentPath = "$_parentProperty.$path"
            _parentModel!!.notifyChanged(parentPath, property, tag, oldValue, newValue)
        }
    }

    /**
     * Serialize this model to a [JSONObject], recursively if required.
     *
     * @return The resulting [JSONObject].
     */
    fun toJSON(): JSONObject {
        val jsonObject = JSONObject()
        synchronized(data) {
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
        }
        return jsonObject
    }

    override fun subscribe(handler: IModelChangedHandler) = changeNotifier.subscribe(handler)

    override fun unsubscribe(handler: IModelChangedHandler) = changeNotifier.unsubscribe(handler)

    override val hasSubscribers: Boolean
        get() = changeNotifier.hasSubscribers
}
