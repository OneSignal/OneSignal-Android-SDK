package com.onesignal.onesignal.internal.modeling

import com.onesignal.onesignal.internal.common.BaseNotifyChanged
import kotlin.collections.HashMap

open class Model : BaseNotifyChanged<ModelChangedArgs>() {

    var id: String
        get() = get(::id.name)
        set(value) { set(::id.name, value) }

    private val _data: HashMap<String, Any?> = HashMap()

    protected fun <T> set(name: String, value: T) {
        val oldValue = _data[name]
        val newValue = value as Any?

        _data[name] = newValue

        val changeArgs = ModelChangedArgs(this, name, oldValue, newValue)
        onChanged(changeArgs)
    }

    protected fun <T> get(name: String) : T {
        return _data[name] as T
    }

    protected fun <T> get(name: String, create: () -> T) : T {
        return if(_data.containsKey(name))
            _data[name] as T
        else {
            val defaultValue = create()
            _data[name] = defaultValue as Any?
            defaultValue
        }
    }
}