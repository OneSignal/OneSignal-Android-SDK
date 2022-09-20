package com.onesignal.core.internal.backend.impl

import com.onesignal.core.internal.backend.IIdentityBackendService

internal class IdentityBackendService : IIdentityBackendService {
    override suspend fun createAlias(appId: String, aliasLabel: String, aliasValue: String, identities: Map<String, String>): Map<String, String> {
        // TODO: To Implement
        return identities
    }

    override suspend fun updateAlias(appId: String, aliasLabel: String, aliasValue: String, aliasLabelToUpdate: String, newAliasId: String) {
        // TODO: To Implement
    }

    override suspend fun deleteAlias(appId: String, aliasLabel: String, aliasValue: String, aliasLabelToDelete: String) {
        // TODO: To Implement
    }
}
