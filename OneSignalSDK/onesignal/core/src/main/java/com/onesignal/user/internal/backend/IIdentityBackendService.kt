package com.onesignal.user.internal.backend

import com.onesignal.common.exceptions.BackendException

interface IIdentityBackendService {
    /**
     * Set one or more aliases for the user identified by the [aliasLabel]/[aliasValue] provided.
     *
     * If there is a non-successful response from the backend, a [BackendException] will be thrown with response data.
     *
     * @param appId The ID of the OneSignal application this user exists under.
     * @param aliasLabel The alias label to retrieve the user under.
     * @param aliasValue The identifier within the [aliasLabel] that identifies the user to retrieve.
     * @param identities The identities that are to be created.
     */
    suspend fun setAlias(
        appId: String,
        aliasLabel: String,
        aliasValue: String,
        identities: Map<String, String>,
    ): Map<String, String>

    /**
     * Delete the [aliasLabelToDelete] from the user identified by the [aliasLabel]/[aliasValue] provided.
     *
     * If there is a non-successful response from the backend, a [BackendException] will be thrown with response data.
     *
     * @param appId The ID of the OneSignal application this user exists under.
     * @param aliasLabel The alias label to retrieve the user under.
     * @param aliasValue The identifier within the [aliasLabel] that identifies the user to retrieve.
     * @param aliasLabelToDelete The alias label to delete from the user identified.
     */
    suspend fun deleteAlias(
        appId: String,
        aliasLabel: String,
        aliasValue: String,
        aliasLabelToDelete: String,
    )
}

object IdentityConstants {
    /**
     * The alias label for the external ID alias.
     */
    const val EXTERNAL_ID = "external_id"

    /**
     * The alias label for the internal onesignal ID alias.
     */
    const val ONESIGNAL_ID = "onesignal_id"
}
