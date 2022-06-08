package com.onesignal.user.internal.models

import com.onesignal.models.modeling.Model

class UserModel : Model() {

    private var data: HashMap<String, Any> = HashMap()

    var language: String
        get() = getProperty(::language.name)
        set(value) { setProperty(::language.name, value) }



    private fun <T> getProperty(name: String) : T {
        return data[name] as T
    }

    private fun <T> setProperty(name: String, value: T) {
        data[name] = value as Any
    }
}