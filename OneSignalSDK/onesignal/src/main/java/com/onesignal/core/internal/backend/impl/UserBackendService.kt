package com.onesignal.core.internal.backend.impl

import com.onesignal.core.internal.backend.CreateUserResponse
import com.onesignal.core.internal.backend.ISubscriptionBackendService
import com.onesignal.core.internal.backend.IUserBackendService
import com.onesignal.core.internal.backend.IdentityConstants
import com.onesignal.core.internal.backend.PropertiesDeltasObject
import com.onesignal.core.internal.backend.PropertiesObject
import com.onesignal.core.internal.backend.SubscriptionObject
import com.onesignal.core.internal.models.SubscriptionModel
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

internal class UserBackendService(
    private val _subscriptionBackend: ISubscriptionBackendService
) : IUserBackendService {

    override suspend fun createUser(appId: String, identities: Map<String, String>, properties: PropertiesObject, subscriptions: List<SubscriptionObject>): CreateUserResponse {
        // Eventually....
        val propertiesObject = createPropertiesJSON(properties)
        val identityObject = JSONObject()
        var subscriptionsArray = createSubscriptionArrayJSON(subscriptions)

        // identity
        for (identity in identities) {
            identityObject.put(identity.key, identity.value)
        }

        val requestJSON = JSONObject()
            .put("properties", propertiesObject)
            .put("identity", identityObject)
            .put("subscriptions", subscriptionsArray)

        // TODO: Temporarily using players endpoint via subscription backend to register the subscription, so we can drive push/IAMs.
        val subscriptionIDs = mutableListOf<String>()
        for (subscription in subscriptions) {
            val subscriptionId = _subscriptionBackend.createSubscription(appId, "", "", subscription.type, subscription.enabled ?: true, subscription.token ?: "", subscription.notificationTypes ?: SubscriptionModel.STATUS_SUBSCRIBED)
            subscriptionIDs.add(subscriptionId)
        }

        // SCAFFOLD: Generate a new onesignal ID from the "backend"
        val mutIdentities = identities.toMutableMap()
        mutIdentities[IdentityConstants.ONESIGNAL_ID] = UUID.randomUUID().toString()

        return CreateUserResponse(mutIdentities, properties, subscriptionIDs)
    }

    override suspend fun updateUser(appId: String, aliasLabel: String, aliasValue: String, properties: PropertiesObject, refreshDeviceMetadata: Boolean, propertyiesDelta: PropertiesDeltasObject) {
        val propertiesObject = createPropertiesJSON(properties)
        val deltasObject = createPropertiesDeltaJSON(propertyiesDelta)

        val jsonObject = JSONObject()
            .put("properties", propertiesObject)
            .put("refresh_device_metadata", refreshDeviceMetadata)
            .put("deltas", deltasObject)

        // TODO: To Implement: Call backend with jsonObject and deserialize response
    }

    private fun createPropertiesJSON(properties: PropertiesObject): JSONObject {
        val propertiesObject = JSONObject()

        if (properties.tags != null && properties.tags.isNotEmpty()) {
            val tagsObject = JSONObject()
            for (tag in properties.tags) {
                tagsObject.put(tag.key, tag.value)
            }
            propertiesObject.put("tags", tagsObject)
        }

        if (properties.language != null) {
            propertiesObject.put("language", properties.language)
        }

        if (properties.timezoneId != null) {
            propertiesObject.put("timezone_id", properties.timezoneId)
        }

        if (properties.latitude != null) {
            propertiesObject.put("lat", properties.latitude)
        }

        if (properties.longitude != null) {
            propertiesObject.put("long", properties.longitude)
        }

        if (properties.country != null) {
            propertiesObject.put("country", properties.country)
        }

        return propertiesObject
    }

    private fun createPropertiesDeltaJSON(propertiesDeltas: PropertiesDeltasObject): JSONObject {
        val deltasObject = JSONObject()

        if (propertiesDeltas.sessionTime != null) {
            deltasObject.put("session_time", propertiesDeltas.sessionTime)
        }

        if (propertiesDeltas.sessionCounts != null) {
            deltasObject.put("session_counts", propertiesDeltas.sessionCounts)
        }

        if (propertiesDeltas.amountSpent != null) {
            deltasObject.put("amount_spent", propertiesDeltas.amountSpent)
        }

        if (propertiesDeltas.purchases != null) {
            val purchasesArray = JSONArray()
            for (purchase in propertiesDeltas.purchases) {
                val jsonItem = JSONObject()
                jsonItem.put("sku", purchase.sku)
                jsonItem.put("iso", purchase.iso)
                jsonItem.put("amount", purchase.amount.toString())
                purchasesArray.put(jsonItem)
            }
            deltasObject.put("purchases", purchasesArray)
        }

        return deltasObject
    }

    private fun createSubscriptionArrayJSON(subscriptions: List<SubscriptionObject>): JSONArray {
        var subscriptionsArray = JSONArray()

        for (subscription in subscriptions) {
            val subscriptionObject = JSONObject()
            subscriptionObject.put("id", subscription.id)
            subscriptionObject.put("type", subscription.type.value)

            if (subscription.token != null) {
                subscriptionObject.put("token", subscription.token)
            }

            if (subscription.enabled != null) {
                subscriptionObject.put("enabled", subscription.enabled)
            }

            if (subscription.notificationTypes != null) {
                subscriptionObject.put("notification_types", subscription.notificationTypes)
            }

            if (subscription.sdk != null) {
                subscriptionObject.put("sdk", subscription.sdk)
            }

            if (subscription.deviceModel != null) {
                subscriptionObject.put("device_model", subscription.deviceModel)
            }

            if (subscription.deviceOS != null) {
                subscriptionObject.put("device_os", subscription.deviceOS)
            }

            if (subscription.rooted != null) {
                subscriptionObject.put("rooted", subscription.rooted)
            }

            if (subscription.testType != null) {
                subscriptionObject.put("test_type", subscription.testType)
            }

            if (subscription.appVersion != null) {
                subscriptionObject.put("app_version", subscription.appVersion)
            }

            if (subscription.netType != null) {
                subscriptionObject.put("net_type", subscription.netType)
            }

            if (subscription.carrier != null) {
                subscriptionObject.put("carrier", subscription.carrier)
            }

            if (subscription.webAuth != null) {
                subscriptionObject.put("web_auth", subscription.webAuth)
            }

            if (subscription.webP256 != null) {
                subscriptionObject.put("web_p256", subscription.webP256)
            }

            subscriptionsArray.put(subscriptionObject)
        }

        return subscriptionsArray
    }
}
