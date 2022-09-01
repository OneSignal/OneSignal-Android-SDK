package com.onesignal.iam.internal.repositories

import com.onesignal.iam.internal.InAppMessage

/**
 * Provides access to the In App Message Repository.  This repository is currently used to
 * store all In App Messages that have been displayed to the user, for purposes of tracking
 * behavioral data around that IAM (the stats on an IAM that hasn't been displayed is known,
 * therefore does not need to be saved).
 */
internal interface IInAppRepository {
    /**
     * Save the provided In App Message to the repository.
     *
     * @param inAppMessage: The message that is to be saved into the repository.
     */
    suspend fun saveInAppMessage(inAppMessage: InAppMessage)

    /**
     * List all In App messages that exist within the repository.
     */
    suspend fun listInAppMessages(): List<InAppMessage>

    /**
     * Clean up (delete) all stale in app messages.
     */
    // TODO: This needs to be driven
    suspend fun cleanCachedInAppMessages()
}
