package com.onesignal.core.internal.modeling

import com.onesignal.core.internal.common.events.EventProducer
import com.onesignal.core.internal.common.events.IEventNotifier
import com.onesignal.core.internal.common.events.IEventProducer

internal open class Model(
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

    /**
     * Access to the underlying data, be sure to use [setProperty] and [getProperty]
     * when accessing the underlying data.
     */
    val data: MutableMap<String, Any?> = mutableMapOf()

    private val _changeNotifier: IEventProducer<IModelChangedHandler> = EventProducer()

    init {
        if (_parentModel != null && _parentProperty == null) {
            throw Exception("If parent model is set, parent property must also be set.")
        } else if (_parentModel == null && _parentProperty != null) {
            throw Exception("If parent property is set, parent model must also be set.")
        }
    }

    /**
     * Set a property on this model to the provided value, with the ability to prevent
     * firing the notification of the change.
     *
     * Note, this should not be used directly unless you know what you're doing.
     *
     * @param name: The name of the property that is to be set.
     * @param value: The value of the property to set it to.
     * @param notify: Whether to notify subscribers of the change.
     */
    fun <T> setProperty(name: String, value: T, notify: Boolean = true) {
        val oldValue = data[name]
        val newValue = value as Any?

        if (newValue != null) {
            data[name] = newValue
        } else if (data.containsKey(name)) {
            data.remove(name)
        }

        if (notify) {
            notifyChanged(name, name, oldValue, newValue)
        }
    }

    /**
     * Retrieve the property on this model with the provided name.
     *
     * @param name: The name of the property that is to be retrieved.
     * @param create: An optional lambda to provide which will be called to create a default
     * value for the property, in the event it doesn't exist yet.
     *
     * @return The value for this property.
     */
    protected fun <T> getProperty(name: String, create: (() -> T)? = null): T {
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

    private fun notifyChanged(path: String, property: String, oldValue: Any?, newValue: Any?) {
        // if there are any changed listeners for this specific model, notify them.
        val changeArgs = ModelChangedArgs(this, path, property, oldValue, newValue)
        _changeNotifier.fire { it.onChanged(changeArgs) }

        // if there is a parent model, propagate the change up to the parent for it's own processing.
        if (_parentModel != null) {
            val parentPath = "$_parentProperty.$path"
            _parentModel.notifyChanged(parentPath, property, oldValue, newValue)
        }
    }

    override fun subscribe(handler: IModelChangedHandler) = _changeNotifier.subscribe(handler)
    override fun unsubscribe(handler: IModelChangedHandler) = _changeNotifier.unsubscribe(handler)
}
