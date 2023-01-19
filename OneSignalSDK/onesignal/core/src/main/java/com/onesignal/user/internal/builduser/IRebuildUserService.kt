package com.onesignal.user.internal.builduser

import com.onesignal.core.internal.operations.Operation

interface IRebuildUserService {
    /**
     * Retrieve the list of operations for rebuilding a user, if the
     * [onesignalId] provided represents the current user.
     *
     * @param appId The id of the app.
     * @param onesignalId The id of the user to retrieve operations for.
     *
     * @return the list of operations if [onesignalId] represents the current
     * user, null otherwise.
     */
    fun getRebuildOperationsIfCurrentUser(appId: String, onesignalId: String): List<Operation>?
}
