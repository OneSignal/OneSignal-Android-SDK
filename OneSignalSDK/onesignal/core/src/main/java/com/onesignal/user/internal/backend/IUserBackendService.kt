package com.onesignal.user.internal.backend

import com.onesignal.common.consistency.RywData
import com.onesignal.common.exceptions.BackendException

interface IUserBackendService {
    /**
     * Create a user on the backend. If an identity provided already exists, this will result in *updating* that
     * user and retrieving the net result as the response. Note that there must be at least 1 identity *or* 1 subscription.
     *
     * If there is a non-successful response from the backend, a [BackendException] will be thrown with response data.
     *
     * @param appId The ID of the OneSignal application this user will be created under.
     * @param identities The identities to retrieve/modify this user in subsequent requests.  Each identity key/value pair must be unique within
     * the application.
     * @param subscriptions The subscriptions that should also be created and associated with the user. If subscriptions are already owned by a different user
     * they will be transferred to this user.
     * @param properties The properties for this user. For new users this should include the timezone_id property.
     *
     * @return The backend response
     */
    suspend fun createUser(
        appId: String,
        identities: Map<String, String>,
        subscriptions: List<SubscriptionObject>,
        properties: Map<String, String>,
    ): CreateUserResponse
    // TODO: Change to send only the push subscription, optimally

    /**
     * Update the user identified by the [appId]/[aliasId]/[aliasLabel] on the backend.
     *
     * If there is a non-successful response from the backend, a [BackendException] will be thrown with response data.
     *
     * @param appId The ID of the OneSignal application this user will be created under.
     * @param aliasLabel The alias label to retrieve the user under.
     * @param aliasValue The identifier within the [aliasLabel] that identifies the user to retrieve.
     * @param properties The properties for this user.
     * @param refreshDeviceMetadata Whether the backend should refresh the device metadata for this user.
     * @param propertyiesDelta The properties delta for this user.
     *
     * @return The updated properties for this user.
     */
    suspend fun updateUser(
        appId: String,
        aliasLabel: String,
        aliasValue: String,
        properties: PropertiesObject,
        refreshDeviceMetadata: Boolean,
        propertyiesDelta: PropertiesDeltasObject,
    ): RywData

    /**
     * Retrieve a user from the backend.
     *
     * If there is a non-successful response from the backend, a [BackendException] will be thrown with response data.
     *
     * @param appId The ID of the OneSignal application this user will be created under.
     * @param aliasLabel The alias label to retrieve the user under.
     * @param aliasValue The identifier within the [aliasLabel] that identifies the user to retrieve.
     *
     * @return The backend response
     */
    suspend fun getUser(
        appId: String,
        aliasLabel: String,
        aliasValue: String,
    ): CreateUserResponse
}

class CreateUserResponse(
    /**
     * The identities for the created user.
     */
    val identities: Map<String, String>,
    /**
     * The properties for the user.
     */
    val properties: PropertiesObject,
    /**
     * The subscriptions for the user.
     */
    val subscriptions: List<SubscriptionObject>,
)
