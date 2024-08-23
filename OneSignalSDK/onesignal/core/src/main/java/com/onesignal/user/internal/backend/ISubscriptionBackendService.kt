package com.onesignal.user.internal.backend

import com.onesignal.common.exceptions.BackendException

interface ISubscriptionBackendService {
    /**
     * Create a new subscription for the user identified by the [aliasLabel]/[aliasValue] provided. If the subscription
     * being created already exists under a different user, ownership will be transferred to this user provided.
     *
     * If there is a non-successful response from the backend, a [BackendException] will be thrown with response data.
     *
     * @param appId The ID of the OneSignal application this user exists under.
     * @param aliasLabel The alias label to retrieve the user under.
     * @param aliasValue The identifier within the [aliasLabel] that identifies the user to retrieve.
     * @param subscription The subscription to create.
     *
     * @return The ID of the subscription created.  Or null if the subscription is already part of the current user.
     */
    suspend fun createSubscription(
        appId: String,
        aliasLabel: String,
        aliasValue: String,
        subscription: SubscriptionObject,
    ): Pair<String, Long?>?

    /**
     * Update an existing subscription with the properties provided.
     *
     * @param appId The ID of the OneSignal application this subscription exists under.
     * @param subscriptionId The ID of the subscription to update.
     * @param subscription The subscription updates. Any non-null value will be updated.
     */
    suspend fun updateSubscription(
        appId: String,
        subscriptionId: String,
        subscription: SubscriptionObject,
    ): Long?

    /**
     * Delete an existing subscription.
     *
     * @param appId The ID of the OneSignal application this subscription exists under.
     * @param subscriptionId The ID of the subscription to delete.
     */
    suspend fun deleteSubscription(
        appId: String,
        subscriptionId: String,
    )

    /**
     * Transfer an existing subscription to the user specified.
     *
     * @param appId The ID of the OneSignal application this subscription exists under.
     * @param subscriptionId The ID of the subscription to transfer.
     * @param aliasLabel The alias label of the user to transfer the subscription under.
     * @param aliasValue The identifier within the [aliasLabel] that identifies the user to transfer under.
     */
    suspend fun transferSubscription(
        appId: String,
        subscriptionId: String,
        aliasLabel: String,
        aliasValue: String,
    )

    /**
     * Given an existing subscription, retrieve all identities associated to it.
     *
     * @param appId The ID of the OneSignal application this subscription exists under.
     * @param subscriptionId The ID of the subscription to retrieve identities for.
     *
     * @return The identities associated to the subscription.
     */
    suspend fun getIdentityFromSubscription(
        appId: String,
        subscriptionId: String,
    ): Map<String, String>
}
