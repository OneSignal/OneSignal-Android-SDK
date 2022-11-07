package com.onesignal.user.internal.operations.impl.executors

import com.onesignal.user.internal.backend.PropertiesObject
import com.onesignal.user.internal.operations.DeleteTagOperation
import com.onesignal.user.internal.operations.SetPropertyOperation
import com.onesignal.user.internal.operations.SetTagOperation
import com.onesignal.user.internal.properties.PropertiesModel

internal object PropertyOperationHelper {
    fun createPropertiesFromOperation(operation: SetTagOperation, propertiesObject: PropertiesObject): PropertiesObject {
        var tags = propertiesObject.tags?.toMutableMap()
        if (tags == null) {
            tags = mutableMapOf()
        }

        tags[operation.key] = operation.value
        return PropertiesObject(tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude)
    }

    fun createPropertiesFromOperation(operation: DeleteTagOperation, propertiesObject: PropertiesObject): PropertiesObject {
        var tags = propertiesObject.tags?.toMutableMap()
        tags?.remove(operation.key)
        return PropertiesObject(tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude)
    }

    fun createPropertiesFromOperation(operation: SetPropertyOperation, propertiesObject: PropertiesObject): PropertiesObject {
        return when (operation.property) {
            PropertiesModel::language.name -> PropertiesObject(propertiesObject.tags, operation.value?.toString(), propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude, propertiesObject.locationAccuracy, propertiesObject.locationType, propertiesObject.locationBackground, propertiesObject.locationTimestamp)
            PropertiesModel::timezone.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, operation.value?.toString(), propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude, propertiesObject.locationAccuracy, propertiesObject.locationType, propertiesObject.locationBackground, propertiesObject.locationTimestamp)
            PropertiesModel::country.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, operation.value?.toString(), propertiesObject.latitude, propertiesObject.longitude, propertiesObject.locationAccuracy, propertiesObject.locationType, propertiesObject.locationBackground, propertiesObject.locationTimestamp)
            PropertiesModel::locationLatitude.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, operation.value?.toString()?.toDouble(), propertiesObject.longitude, propertiesObject.locationAccuracy, propertiesObject.locationType, propertiesObject.locationBackground, propertiesObject.locationTimestamp)
            PropertiesModel::locationLongitude.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, operation.value?.toString()?.toDouble(), propertiesObject.locationAccuracy, propertiesObject.locationType, propertiesObject.locationBackground, propertiesObject.locationTimestamp)
            PropertiesModel::locationAccuracy.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude, operation.value?.toString()?.toFloat(), propertiesObject.locationType, propertiesObject.locationBackground, propertiesObject.locationTimestamp)
            PropertiesModel::locationType.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude, propertiesObject.locationAccuracy, operation.value?.toString()?.toInt(), propertiesObject.locationBackground, propertiesObject.locationTimestamp)
            PropertiesModel::locationBackground.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude, propertiesObject.locationAccuracy, propertiesObject.locationType, operation.value?.toString()?.toBoolean(), propertiesObject.locationTimestamp)
            PropertiesModel::locationTimestamp.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude, propertiesObject.locationAccuracy, propertiesObject.locationType, propertiesObject.locationBackground, operation.value?.toString()?.toLong())
            else -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude, propertiesObject.locationAccuracy, propertiesObject.locationType, propertiesObject.locationBackground, propertiesObject.locationTimestamp)
        }
    }
}
