package com.onesignal.onesignal.internal.backend.api

import com.onesignal.onesignal.internal.backend.http.IHttpClient
import com.onesignal.onesignal.logging.Logging
import org.json.JSONException
import org.json.JSONObject

class ApiService(private val _httpClient: IHttpClient) : IApiService {
    override suspend fun createUserAsync(user: Any) {
        TODO("Not yet implemented")
    }

    override suspend fun updateUserAsync(aliasLabel: String, aliasId: String, user: Any) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteUserAsync(aliasLabel: String, aliasId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun setUserIdentityAsync(
        aliasLabel: String,
        aliasId: String,
        identity: Any
    ): Any {
        TODO("Not yet implemented")
    }

    override suspend fun getUserIdentityAsync(aliasLabel: String, aliasId: String): Any {
        TODO("Not yet implemented")
    }

    override suspend fun updateUserAliasAsync(
        aliasLabel: String,
        aliasId: String,
        newAliasId: String
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteUserAliasAsync(aliasLabel: String, aliasId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun createSubscriptionAsync(
        aliasLabel: String,
        aliasId: String,
        subscription: Any
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteSubscriptionAsync(subscriptionId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun getIdentityFromSubscriptionAsync(subscriptionId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun createIdentityForSubscriptionAsync(subscriptionId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun updateIdentityForSubscriptionAsync(subscriptionId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteSubscriptionPropertiesAsync(subscriptionId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun listIAMsByUserAsync(aliasLabel: String, aliasId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun listIAMsBySubscriptionAsync(subscriptionId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun getSubscriptionAsync() {
        TODO("Not yet implemented")
    }

    override suspend fun updateSubscriptionAsync() {
        TODO("Not yet implemented")
    }

    override suspend fun logoutEmailAsync() {
        TODO("Not yet implemented")
    }

    override suspend fun createPurchaseAsync() {
        TODO("Not yet implemented")
    }

    override suspend fun createSessionAsync() {
        TODO("Not yet implemented")
    }

    override suspend fun updateSessionAsync() {
        TODO("Not yet implemented")
    }

    override suspend fun createFocusAsync() {
        TODO("Not yet implemented")
    }

    override suspend fun createNotificationAsync() {
        TODO("Not yet implemented")
    }

    override suspend fun updateNotificationAsOpenedAsync() {
        TODO("Not yet implemented")
    }

    override suspend fun updateNotificationAsReceived(appId: String, notificationId: String, subscriptionId: String, deviceType: Int?) {
        try {
            val jsonBody = JSONObject()
                .put("app_id", appId)
                .put("player_id", subscriptionId)
            if (deviceType != null) {
                jsonBody.put("device_type", deviceType)
            }

            val response = _httpClient.put("notifications/$notificationId/report_received", jsonBody)

        } catch (e: JSONException) {
            Logging.error("Generating direct receive receipt:JSON Failed.", e)
        }
    }

    override suspend fun updateIAMAsClickedAsync() {
        TODO("Not yet implemented")
    }

    override suspend fun updateIAMAsPageImpressionAsync() {
        TODO("Not yet implemented")
    }

    override suspend fun updateIAMAsImpressionAsync() {
        TODO("Not yet implemented")
    }

    override suspend fun getIAMPreviewDataAsync() {
        TODO("Not yet implemented")
    }

    override suspend fun getIAMDataAsync() {
        TODO("Not yet implemented")
    }

    override suspend fun getParamsAsync() {
        TODO("Not yet implemented")
    }

}
