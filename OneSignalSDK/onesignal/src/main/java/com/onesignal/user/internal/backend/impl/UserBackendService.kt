package com.onesignal.user.internal.backend.impl

import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.putJSONObject
import com.onesignal.common.putMap
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.user.internal.backend.CreateUserResponse
import com.onesignal.user.internal.backend.IUserBackendService
import com.onesignal.user.internal.backend.PropertiesDeltasObject
import com.onesignal.user.internal.backend.PropertiesObject
import com.onesignal.user.internal.backend.SubscriptionObject
import org.json.JSONObject

internal class UserBackendService(
    private val _httpClient: IHttpClient
) : IUserBackendService {

    override suspend fun createUser(appId: String, identities: Map<String, String>, properties: PropertiesObject, subscriptions: List<SubscriptionObject>): CreateUserResponse {
        val requestJSON = JSONObject()

        if (identities.isNotEmpty()) {
            requestJSON.put("identity", JSONObject().putMap(identities))
        }

        if (properties.hasAtLeastOnePropertySet) {
            requestJSON.put("properties", JSONConverter.convertToJSON(properties))
        }

        if (subscriptions.isNotEmpty()) {
            requestJSON
                .put("subscriptions", JSONConverter.convertToJSON(subscriptions))
                .putJSONObject("subscription_options") {
                    it.put("retain_previous_owner", true)
                }
        }

        val response = _httpClient.post("apps/$appId/users", requestJSON)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload)
        }

        return JSONConverter.convertToCreateUserResponse(JSONObject(response.payload!!))
    }

    override suspend fun updateUser(appId: String, aliasLabel: String, aliasValue: String, properties: PropertiesObject, refreshDeviceMetadata: Boolean, propertyiesDelta: PropertiesDeltasObject) {
        val jsonObject = JSONObject()
            .put("refresh_device_metadata", refreshDeviceMetadata)

        if (properties.hasAtLeastOnePropertySet) {
            jsonObject.put("properties", JSONConverter.convertToJSON(properties))
        }

        if (propertyiesDelta.hasAtLeastOnePropertySet) {
            jsonObject.put("deltas", JSONConverter.convertToJSON(propertyiesDelta))
        }

        val response = _httpClient.patch("apps/$appId/users/by/$aliasLabel/$aliasValue", jsonObject)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload)
        }
    }

    override suspend fun getUser(appId: String, aliasLabel: String, aliasValue: String): CreateUserResponse {
        val response = _httpClient.get("apps/$appId/users/by/$aliasLabel/$aliasValue")

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload)
        }

        return JSONConverter.convertToCreateUserResponse(JSONObject(response.payload))
    }
}
