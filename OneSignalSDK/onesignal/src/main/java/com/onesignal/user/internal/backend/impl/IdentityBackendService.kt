package com.onesignal.user.internal.backend.impl

import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.user.internal.backend.IIdentityBackendService
import kotlinx.coroutines.yield

internal class IdentityBackendService(
    private val _httpClient: IHttpClient
) : IIdentityBackendService {
    override suspend fun createAlias(appId: String, aliasLabel: String, aliasValue: String, identities: Map<String, String>): Map<String, String> {
        // TODO: To Implement
        yield()
        return identities
    }

    override suspend fun updateAlias(appId: String, aliasLabel: String, aliasValue: String, aliasLabelToUpdate: String, newAliasId: String) {
        // TODO: To Implement
        yield()
    }

    override suspend fun deleteAlias(appId: String, aliasLabel: String, aliasValue: String, aliasLabelToDelete: String) {
        // TODO: To Implement
        yield()
    }
}
