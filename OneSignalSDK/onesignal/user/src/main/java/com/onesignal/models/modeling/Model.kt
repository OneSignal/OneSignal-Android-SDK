package com.onesignal.models.modeling

open class Model {
    private val data: HashMap<String, String> = HashMap()

    protected fun set(name: String, value: String) {
        data[name] = value
    }
}