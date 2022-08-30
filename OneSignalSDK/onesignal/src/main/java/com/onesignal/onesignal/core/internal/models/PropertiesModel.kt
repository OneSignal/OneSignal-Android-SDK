package com.onesignal.onesignal.core.internal.models

import com.onesignal.onesignal.core.internal.modeling.Model

class PropertiesModel : Model() {
    var language: String
        get() = get(::language.name) { "en" }
        set(value) { set(::language.name, value) }

    var country: String
        get() = get(::country.name) { "US" }
        set(value) { set(::country.name, value) }

    var timezone: UInt?
        get() = get(::timezone.name)
        set(value) { set(::timezone.name, value) }

    var tags: Map<String, String>
        get() = get(::tags.name) { mapOf() }
        set(value) { set(::tags.name, value) }

    var locationLatitude: Double?
        get() = get(::locationLatitude.name)
        set(value) { set(::locationLatitude.name, value) }

    var locationLongitude: Double?
        get() = get(::locationLongitude.name)
        set(value) { set(::locationLongitude.name, value) }

    var locationAccuracy: Float?
        get() = get(::locationAccuracy.name)
        set(value) { set(::locationAccuracy.name, value) }

    var locationType: Int?
        get() = get(::locationType.name)
        set(value) { set(::locationType.name, value) }

    var locationBackground: Boolean?
        get() = get(::locationBackground.name)
        set(value) { set(::locationBackground.name, value) }

    var locationTimestamp: Long?
        get() = get(::locationTimestamp.name)
        set(value) { set(::locationTimestamp.name, value) }
}
