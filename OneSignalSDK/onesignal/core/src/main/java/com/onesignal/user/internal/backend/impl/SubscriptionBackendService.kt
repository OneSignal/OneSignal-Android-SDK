package com.onesignal.user.internal.backend.impl

import com.onesignal.common.consistency.RywData
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
    ): Pair<String, RywData>? {
        val jsonSubscription = JSONConverter.convertToJSON(subscription)
        jsonSubscription.remove("id")
        val requestJSON = JSONObject().put("subscription", jsonSubscription)

        val response = _httpClient.post("apps/$appId/users/by/$aliasLabel/$aliasValue/subscriptions", requestJSON)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }

        val responseJSON = JSONObject(response.payload!!)
        val subscriptionJSON = responseJSON.safeJSONObject("subscription")
        if (subscriptionJSON == null || !subscriptionJSON.has("id")) {
            return null
        }

        var rywToken: String? = null
        if (responseJSON.has("ryw_token")) {
            rywToken = responseJSON.getString("ryw_token")
        }

        var rywDelay: Long? = null
        if (responseJSON.has("ryw_delay")) {
            rywDelay = responseJSON.getLong("ryw_delay")
        }

        var rywData = RywData(rywToken, rywDelay)

        return Pair(subscriptionJSON.getString("id"), rywData)
    }

    override suspend fun updateSubscription(
        appId: String,
        subscriptionId: String,
        subscription: SubscriptionObject,
    ): RywData {
        val requestJSON =
            JSONObject()
                .put("subscription", JSONConverter.convertToJSON(subscription))

        val response = _httpClient.patch("apps/$appId/subscriptions/$subscriptionId", requestJSON)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }

        val responseJSON = JSONObject(response.payload)
        var rywToken: String? = null
        if (responseJSON.has("ryw_token")) {
            rywToken = responseJSON.getString("ryw_token")
        }

        var rywDelay: Long? = null
        if (responseJSON.has("ryw_delay")) {
            rywDelay = responseJSON.getLong("ryw_delay")
        }

        return RywData(rywToken, rywDelay)
    }

    override suspend fun deleteSubscription(
        appId: String,
        subscriptionId: String,
    ) {
        val response = _httpClient.delete("apps/$appId/subscriptions/$subscriptionId")

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }
    }

    override suspend fun transferSubscription(
        appId: String,
        subscriptionId: String,
        aliasLabel: String,
        aliasValue: String,
    ) {
        val requestJSON =
            JSONObject()
                .put("identity", JSONObject().put(aliasLabel, aliasValue))

        val response = _httpClient.patch("apps/$appId/subscriptions/$subscriptionId/owner", requestJSON)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }
    }

    override suspend fun getIdentityFromSubscription(
        appId: String,
        subscriptionId: String,
    ): Map<String, String> {
        val response = _httpClient.get("apps/$appId/subscriptions/$subscriptionId/user/identity")

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }

        val responseJSON = JSONObject(response.payload!!)
        val identityJSON = responseJSON.safeJSONObject("identity")
        return identityJSON?.toMap()?.mapValues { it.value.toString() } ?: mapOf()
    }
}
