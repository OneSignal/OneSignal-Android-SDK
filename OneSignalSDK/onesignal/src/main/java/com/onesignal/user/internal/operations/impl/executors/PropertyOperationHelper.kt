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
        if (tags == null) {
            tags = mutableMapOf()
        }
        tags[operation.key] = null
        return PropertiesObject(tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude)
    }

    fun createPropertiesFromOperation(operation: SetPropertyOperation, propertiesObject: PropertiesObject): PropertiesObject {
        return when (operation.property) {
            PropertiesModel::language.name -> PropertiesObject(propertiesObject.tags, operation.value?.toString(), propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude)
            PropertiesModel::timezone.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, operation.value?.toString(), propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude)
            PropertiesModel::country.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, operation.value?.toString(), propertiesObject.latitude, propertiesObject.longitude)
            PropertiesModel::locationLatitude.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, operation.value?.toString()?.toDouble(), propertiesObject.longitude)
            PropertiesModel::locationLongitude.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, operation.value?.toString()?.toDouble())
//            PropertiesModel::locationAccuracy.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude)
//            PropertiesModel::locationType.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude)
//            PropertiesModel::locationBackground.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude)
//            PropertiesModel::locationTimestamp.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude)
            else -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude)
        }
    }
}
