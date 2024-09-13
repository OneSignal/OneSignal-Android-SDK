package com.onesignal.user.internal.backend.impl

import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.safeJSONObject
import com.onesignal.common.toMap
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.user.internal.backend.ISubscriptionBackendService
import com.onesignal.user.internal.backend.SubscriptionObject
import org.json.JSONObject

internal class SubscriptionBackendService(
    private val _httpClient: IHttpClient,
) : ISubscriptionBackendService {
    override suspend fun createSubscription(
        appId: String,
        aliasLabel: String,
        aliasValue: String,
        subscription: SubscriptionObject,
        jwt: String?,
    ): String? {
        val jsonSubscription = JSONConverter.convertToJSON(subscription)
        jsonSubscription.remove("id")
        val requestJSON = JSONObject().put("subscription", jsonSubscription)

        val response = _httpClient.post("apps/$appId/users/by/$aliasLabel/$aliasValue/subscriptions", requestJSON, jwt, subscription.token)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }

        val responseJSON = JSONObject(response.payload!!)
        val subscriptionJSON = responseJSON.safeJSONObject("subscription")
        if (subscriptionJSON == null || !subscriptionJSON.has("id")) {
            return null
        }

        return subscriptionJSON.getString("id")
    }

    override suspend fun updateSubscription(
        appId: String,
        subscriptionId: String,
        subscription: SubscriptionObject,
        jwt: String?,
    ) {
        val requestJSON =
            JSONObject()
                .put("subscription", JSONConverter.convertToJSON(subscription))

        val response = _httpClient.patch("apps/$appId/subscriptions/$subscriptionId", requestJSON, jwt, subscription.token)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }
    }

    override suspend fun deleteSubscription(
        appId: String,
        subscriptionId: String,
        jwt: String?,
    ) {
        val response = _httpClient.delete("apps/$appId/subscriptions/$subscriptionId", jwt)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }
    }

    override suspend fun transferSubscription(
        appId: String,
        subscriptionId: String,
        aliasLabel: String,
        aliasValue: String,
        jwt: String?,
    ) {
        val requestJSON =
            JSONObject()
                .put("identity", JSONObject().put(aliasLabel, aliasValue))

        val response = _httpClient.patch("apps/$appId/subscriptions/$subscriptionId/owner", requestJSON, jwt)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }
    }

    override suspend fun getIdentityFromSubscription(
        appId: String,
        subscriptionId: String,
        jwt: String?,
    ): Map<String, String> {
        val response = _httpClient.get("apps/$appId/subscriptions/$subscriptionId/user/identity", jwt)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }

        val responseJSON = JSONObject(response.payload!!)
        val identityJSON = responseJSON.safeJSONObject("identity")
        return identityJSON?.toMap()?.mapValues { it.value.toString() } ?: mapOf()
    }
}
