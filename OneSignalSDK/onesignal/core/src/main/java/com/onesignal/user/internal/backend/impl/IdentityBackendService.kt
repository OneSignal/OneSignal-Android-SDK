package com.onesignal.user.internal.backend.impl

import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.putMap
import com.onesignal.common.toMap
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.user.internal.backend.IIdentityBackendService
import org.json.JSONObject

internal class IdentityBackendService(
    private val _httpClient: IHttpClient,
) : IIdentityBackendService {
    override suspend fun setAlias(
        appId: String,
        aliasLabel: String,
        aliasValue: String,
        identities: Map<String, String>,
        jwt: String?,
    ): Map<String, String> {
        val requestJSONObject =
            JSONObject()
                .put("identity", JSONObject().putMap(identities))

        val response = _httpClient.patch("apps/$appId/users/by/$aliasLabel/$aliasValue/identity", requestJSONObject, jwt)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }

        val responseJSON = JSONObject(response.payload!!)

        return responseJSON.getJSONObject("identity").toMap().mapValues { it.value.toString() }
    }

    override suspend fun deleteAlias(
        appId: String,
        aliasLabel: String,
        aliasValue: String,
        aliasLabelToDelete: String,
        jwt: String?,
    ) {
        val response = _httpClient.delete("apps/$appId/users/by/$aliasLabel/$aliasValue/identity/$aliasLabelToDelete", jwt)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }
    }
}
