package com.onesignal.user.internal.backend.impl

import com.onesignal.common.consistency.RywData
import com.onesignal.common.expandJSONArray
import com.onesignal.common.putJSONArray
import com.onesignal.common.putMap
import com.onesignal.common.putSafe
import com.onesignal.common.safeBool
import com.onesignal.common.safeDouble
import com.onesignal.common.safeInt
import com.onesignal.common.safeJSONObject
import com.onesignal.common.safeLong
import com.onesignal.common.safeString
import com.onesignal.common.toMap
import com.onesignal.user.internal.backend.CreateUserResponse
import com.onesignal.user.internal.backend.PropertiesDeltasObject
import com.onesignal.user.internal.backend.PropertiesObject
import com.onesignal.user.internal.backend.SubscriptionObject
import com.onesignal.user.internal.backend.SubscriptionObjectType
import org.json.JSONArray
import org.json.JSONObject

object JSONConverter {
    fun convertToCreateUserResponse(jsonObject: JSONObject): CreateUserResponse {
        val respIdentities = jsonObject.safeJSONObject("identity")?.toMap()?.mapValues { it.value.toString() } ?: mapOf()

        val propertiesJSON = jsonObject.safeJSONObject("properties")
        val respProperties =
            PropertiesObject(
                propertiesJSON?.safeJSONObject("tags")?.toMap()?.mapValues { it.value.toString() },
                propertiesJSON?.safeString("language"),
                propertiesJSON?.safeString("timezone_id"),
                propertiesJSON?.safeString("country"),
                propertiesJSON?.safeDouble("lat"),
                propertiesJSON?.safeDouble("long"),
            )

        val respSubscriptions =
            jsonObject.expandJSONArray("subscriptions") {
                val subscriptionType = SubscriptionObjectType.fromString(it.getString("type"))
                if (subscriptionType != null) {
                    return@expandJSONArray SubscriptionObject(
                        it.getString("id"),
                        subscriptionType,
                        it.safeString("token"),
                        it.safeBool("enabled"),
                        it.safeInt("notification_types"),
                        it.safeString("sdk"),
                        it.safeString("device_model"),
                        it.safeString("device_os"),
                        it.safeBool("rooted"),
                        it.safeInt("net_type"),
                        it.safeString("carrier"),
                        it.safeString("app_version"),
                    )
                }
                return@expandJSONArray null
            }

        val rywToken = jsonObject.safeString("ryw_token")
        val rywDelay = jsonObject.safeLong("ryw_delay")
        var rywData: RywData? = null

        if (rywToken != null) {
            rywData = RywData(rywToken, rywDelay)
        }

        return CreateUserResponse(respIdentities, respProperties, respSubscriptions, rywData)
    }

    fun convertToJSON(properties: PropertiesObject): JSONObject {
        return JSONObject()
            .putMap("tags", properties.tags)
            .putSafe("language", properties.language)
            .putSafe("timezone_id", properties.timezoneId)
            .putSafe("lat", properties.latitude)
            .putSafe("long", properties.longitude)
            .putSafe("country", properties.country)
    }

    fun convertToJSON(propertiesDeltas: PropertiesDeltasObject): JSONObject {
        return JSONObject()
            .putSafe("session_time", propertiesDeltas.sessionTime)
            .putSafe("session_count", propertiesDeltas.sessionCount)
            .putSafe("amount_spent", propertiesDeltas.amountSpent?.toString())
            .putJSONArray("purchases", propertiesDeltas.purchases) {
                JSONObject()
                    .put("sku", it.sku)
                    .put("iso", it.iso)
                    .put("amount", it.amount.toString())
            }
    }

    fun convertToJSON(subscriptions: List<SubscriptionObject>): JSONArray {
        val subscriptionsArray = JSONArray()

        for (subscription in subscriptions) {
            subscriptionsArray.put(convertToJSON(subscription))
        }

        return subscriptionsArray
    }

    fun convertToJSON(subscription: SubscriptionObject): JSONObject {
        return JSONObject()
            .putSafe("id", subscription.id)
            .putSafe("type", subscription.type?.value)
            .putSafe("token", subscription.token)
            .putSafe("enabled", subscription.enabled)
            .putSafe("notification_types", subscription.notificationTypes)
            .putSafe("sdk", subscription.sdk)
            .putSafe("device_model", subscription.deviceModel)
            .putSafe("device_os", subscription.deviceOS)
            .putSafe("rooted", subscription.rooted)
            .putSafe("net_type", subscription.netType)
            .putSafe("carrier", subscription.carrier)
            .putSafe("app_version", subscription.appVersion)
    }
}
