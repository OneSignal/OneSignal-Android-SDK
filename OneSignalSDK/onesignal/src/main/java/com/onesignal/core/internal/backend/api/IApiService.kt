package com.onesignal.core.internal.backend.api

internal interface IApiService {

    /** NEW ENDPOINTS **/
    /** POST /user **/
    suspend fun createUserAsync(user: Any?)

    /** PATCH /user/by/{aliasLabel}/{aliasId} **/
    suspend fun updateUserAsync(aliasLabel: String, aliasId: String, user: Any?)

    /** DELETE /user/by/{aliasLabel}/{aliasId} **/
    suspend fun deleteUserAsync(aliasLabel: String, aliasId: String)

    /** POST /user/by/{aliasLabel}/{aliasId}/identity **/
    suspend fun setUserIdentityAsync(aliasLabel: String, aliasId: String, identity: Any): Any
    /** GET /user/by/{aliasLabel}/{aliasId}/identity **/
    suspend fun getUserIdentityAsync(aliasLabel: String, aliasId: String): Any

    /** PUT /user/identity/{aliasLabel}/{aliasId} **/
    suspend fun updateUserAliasAsync(aliasLabel: String, aliasId: String, newAliasId: String)
    /** DELETE /user/identity/{aliasLabel}/{aliasId} **/
    suspend fun deleteUserAliasAsync(aliasLabel: String, aliasId: String)

    /** POST /user/by/{aliasLabel}/{aliasId}/subscription **/
    suspend fun createSubscriptionAsync(aliasLabel: String, aliasId: String, subscription: Any?)
    /** DELETE /subscription/{subscriptionId} **/
    suspend fun deleteSubscriptionAsync(subscriptionId: String)
    /** GET /subscription/{subscriptionId}/identity **/
    suspend fun getIdentityFromSubscriptionAsync(subscriptionId: String)
    /** POST /subscription/{subscriptionId}/identity **/
    suspend fun createIdentityForSubscriptionAsync(subscriptionId: String)
    /** PATCH /subscription/{subscriptionId}/identity **/
    suspend fun updateIdentityForSubscriptionAsync(subscriptionId: String)
    /** DELETE /subscription/{subscriptionId}/properties **/
    suspend fun deleteSubscriptionPropertiesAsync(subscriptionId: String)

    /** GET /user/by/{aliasLabel}/{aliasId}/iams **/
    suspend fun listIAMsByUserAsync(aliasLabel: String, aliasId: String)
    /** GET /subscription/{subscriptionId}/iams **/
    suspend fun listIAMsBySubscriptionAsync(subscriptionId: String)

    /** EXISTING ENDPOINTS **/
    /** GET players/{playerId}?app_id={appId} **/
    suspend fun getSubscriptionAsync()
    /** PUT players/{playerId} **/
    suspend fun updateSubscriptionAsync()

    /** POST players/{playerId}/email_logout **/
    suspend fun logoutEmailAsync()

    /** POST players/{playerId}/on_purchase **/
    suspend fun createPurchaseAsync()

    /** POST players **/
    suspend fun createSessionAsync()

    /** POST players/{playerId}/on_session **/
    suspend fun updateSessionAsync()

    /** POST players/{playerId}/on_focus **/
    suspend fun createFocusAsync()

    /** POST notifications **/
    suspend fun createNotificationAsync()

    /** PUT notifications/{id} **/
    suspend fun updateNotificationAsOpenedAsync()

    /**
     * Update the provided notification to indicate it has been received.
     *
     * @param appId The application ID the notification was sent from.
     * @param subscriptionId The id of the subscription that received the notification.
     * @param deviceType The type of device the notification was received on.
     *
     * PUT notifications/{notificationId}/report_received
     */
    suspend fun updateNotificationAsReceived(appId: String, notificationId: String, subscriptionId: String, deviceType: Int?)

    /** POST in_app_messages/{messageId}/click **/
    suspend fun updateIAMAsClickedAsync()

    /** POST in_app_messages/{messageId}/pageImpression **/
    suspend fun updateIAMAsPageImpressionAsync()

    /** POST in_app_messages/{messageId}/impression **/
    suspend fun updateIAMAsImpressionAsync()

    /** GET in_app_messages/device_preview?preview_id={previewUUID}&app_id={appId} **/
    suspend fun getIAMPreviewDataAsync()

    /** GET in_app_messages/{messageId}/variants/{variantId}/html?app_id={appId} **/
    suspend fun getIAMDataAsync()

    /** GET apps/{appId}/android_params.js?player_id={userId} **/
    suspend fun getParamsAsync()
}
