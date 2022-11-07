package com.onesignal.user.internal.properties

import com.onesignal.common.modeling.MapModel
import com.onesignal.common.modeling.Model
import org.json.JSONObject

class PropertiesModel : Model() {
    /**
     * The OneSignal id for the user that is associated with these properties.
     */
    var onesignalId: String
        get() = getProperty(::onesignalId.name)
        set(value) { setProperty(::onesignalId.name, value) }

    /**
     * The language for this user (ISO 639-1 format). When `null` the device default will be used.
     *
     * @see [https://en.wikipedia.org/wiki/ISO_639-1]
     */
    var language: String?
        get() = getProperty(::language.name)
        set(value) { setProperty(::language.name, value) }

    /**
     * The country code for this user (ISO 3166-1 Alpha 2 format).  When `null` the default will
     * be `US`.
     *
     * @see [https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2]
     */
    var country: String
        get() = getProperty(::country.name) { "US" }
        set(value) { setProperty(::country.name, value) }

    /**
     * The timezone for this user (TZ database name).
     *
     * @see [https://en.wikipedia.org/wiki/List_of_tz_database_time_zones]
     */
    var timezone: String?
        get() = getProperty(::timezone.name)
        set(value) { setProperty(::timezone.name, value) }

    /**
     * The data tags for this user.
     */
    val tags: MapModel<String>
        get() = getProperty(::tags.name) { MapModel(this, ::tags.name) }

    /**
     * The user's last known location latitude reading.
     */
    var locationLatitude: Double?
        get() = getProperty(::locationLatitude.name)
        set(value) { setProperty(::locationLatitude.name, value) }

    /**
     * The user's last known location longitude reading.
     */
    var locationLongitude: Double?
        get() = getProperty(::locationLongitude.name)
        set(value) { setProperty(::locationLongitude.name, value) }

    /**
     * The user's last location accuracy reading.
     */
    var locationAccuracy: Float?
        get() = getProperty(::locationAccuracy.name)
        set(value) { setProperty(::locationAccuracy.name, value) }

    /**
     * The user's last location type reading (0 - COARSE, 1 - FINE).
     */
    var locationType: Int?
        get() = getProperty(::locationType.name)
        set(value) { setProperty(::locationType.name, value) }

    /**
     * Whether the user's last location reading was done with the app in the background.
     */
    var locationBackground: Boolean?
        get() = getProperty(::locationBackground.name)
        set(value) { setProperty(::locationBackground.name, value) }

    /**
     * When the user's last location reading was.
     */
    var locationTimestamp: Long?
        get() = getProperty(::locationTimestamp.name)
        set(value) { setProperty(::locationTimestamp.name, value) }

    override fun createModelForProperty(property: String, jsonObject: JSONObject): Model? {
        if (property == ::tags.name) {
            val model = MapModel<String>(this, ::tags.name)
            model.initializeFromJson(jsonObject)
            return model
        }

        return null
    }
}
