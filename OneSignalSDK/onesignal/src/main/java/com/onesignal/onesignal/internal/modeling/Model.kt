package com.onesignal.onesignal.internal.modeling

open class Model {
    var id: String
        get() = get(::id.name)
        set(value) { set(::id.name, value) }

    private val _data: HashMap<String, Any?> = HashMap()

    protected fun <T> set(name: String, value: T) {
        _data[name] = value as Any?
    }

    protected fun <T> get(name: String) : T {
        return _data[name] as T
    }

    protected fun <T> get(name: String, default: T) : T {
        return if(_data.containsKey(name))
            _data[name] as T
        else
            default
    }
}