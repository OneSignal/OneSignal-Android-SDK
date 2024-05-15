package com.onesignal.user.internal.backend.impl

import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.putMap
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.user.internal.backend.CreateUserResponse
import com.onesignal.user.internal.backend.IUserBackendService
import com.onesignal.user.internal.backend.PropertiesDeltasObject
import com.onesignal.user.internal.backend.PropertiesObject
import com.onesignal.user.internal.backend.SubscriptionObject
import org.json.JSONObject

internal class UserBackendService(
    private val _httpClient: IHttpClient,
) : IUserBackendService {
    override suspend fun createUser(
        appId: String,
        identities: Map<String, String>,
        subscriptions: List<SubscriptionObject>,
        properties: Map<String, String>,
        jwt: String?,
    ): CreateUserResponse {
        val requestJSON = JSONObject()

        if (identities.isNotEmpty()) {
            requestJSON.put("identity", JSONObject().putMap(identities))
        }

        if (subscriptions.isNotEmpty()) {
            requestJSON
                .put("subscriptions", JSONConverter.convertToJSON(subscriptions))
        }

        if (properties.isNotEmpty()) {
            requestJSON.put("properties", JSONObject().putMap(properties))
        }

        requestJSON.put("refresh_device_metadata", true)

        val response = _httpClient.post("apps/$appId/users", requestJSON, jwt)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }

        return JSONConverter.convertToCreateUserResponse(JSONObject(response.payload!!))
    }

    override suspend fun updateUser(
        appId: String,
        aliasLabel: String,
        aliasValue: String,
        properties: PropertiesObject,
        refreshDeviceMetadata: Boolean,
        propertyiesDelta: PropertiesDeltasObject,
        jwt: String?,
    ) {
        val jsonObject =
            JSONObject()
                .put("refresh_device_metadata", refreshDeviceMetadata)

        if (properties.hasAtLeastOnePropertySet) {
            jsonObject.put("properties", JSONConverter.convertToJSON(properties))
        }

        if (propertyiesDelta.hasAtLeastOnePropertySet) {
            jsonObject.put("deltas", JSONConverter.convertToJSON(propertyiesDelta))
        }

        val response = _httpClient.patch("apps/$appId/users/by/$aliasLabel/$aliasValue", jsonObject, jwt)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }
    }

    override suspend fun getUser(
        appId: String,
        aliasLabel: String,
        aliasValue: String,
        jwt: String?,
    ): CreateUserResponse {
        val response = _httpClient.get("apps/$appId/users/by/$aliasLabel/$aliasValue", jwt)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }

        return JSONConverter.convertToCreateUserResponse(JSONObject(response.payload))
    }
}
