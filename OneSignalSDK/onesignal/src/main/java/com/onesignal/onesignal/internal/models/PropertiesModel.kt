package com.onesignal.onesignal.internal.models

import com.onesignal.onesignal.internal.modeling.Model

class PropertiesModel : Model() {
    var language: String
        get() = get(::language.name)
        set(value) { set(::language.name, value) }

    var country: String
        get() = get(::country.name)
        set(value) { set(::country.name, value) }

    var timezone: UInt?
        get() = get(::timezone.name)
        set(value) { set(::timezone.name, value) }

    var latitude: Double?
        get() = get(::latitude.name)
        set(value) { set(::latitude.name, value) }

    var longitude: Double?
        get() = get(::longitude.name)
        set(value) { set(::longitude.name, value) }

    var tags: Map<String, String>
        get() = get(::tags.name)
        set(value) { set(::tags.name, value) }
}