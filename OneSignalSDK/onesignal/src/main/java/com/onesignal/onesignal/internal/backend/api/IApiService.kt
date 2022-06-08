package com.onesignal.onesignal.internal.backend.api

interface IApiService  {

    /** NEW ENDPOINTS **/
    /** POST /user **/
    fun createUser(user: UserObject)

    /** PATCH /user/by/{aliasLabel}/{aliasId} **/
    fun updateUser(aliasLabel: String, aliasId: String, user: UserObject)

    /** DELETE /user/by/{aliasLabel}/{aliasId} **/
    fun deleteUser(aliasLabel: String, aliasId: String)

    /** POST /user/by/{aliasLabel}/{aliasId}/identity **/
    fun setUserIdentity(aliasLabel: String, aliasId: String, identity: IdentityObject) : IdentityObject
    /** GET /user/by/{aliasLabel}/{aliasId}/identity **/
    fun getUserIdentity(aliasLabel: String, aliasId: String) : IdentityObject

    /** PUT /user/identity/{aliasLabel}/{aliasId} **/
    fun updateUserAlias(aliasLabel: String, aliasId: String, newAliasId: String)
    /** DELETE /user/identity/{aliasLabel}/{aliasId} **/
    fun deleteUserAlias(aliasLabel: String, aliasId: String)

    /** POST /user/by/{aliasLabel}/{aliasId}/subscription **/
    fun createSubscription(aliasLabel: String, aliasId: String, subscription: SubscriptionObject)
    /** DELETE /subscription/{subscriptionId} **/
    fun deleteSubscription(subscriptionId: String)
    /** GET /subscription/{subscriptionId}/identity **/
    fun getIdentityFromSubscription(subscriptionId: String)
    /** POST /subscription/{subscriptionId}/identity **/
    fun createIdentityForSubscription(subscriptionId: String)
    /** PATCH /subscription/{subscriptionId}/identity **/
    fun updateIdentityForSubscription(subscriptionId: String)
    /** DELETE /subscription/{subscriptionId}/properties **/
    fun deleteSubscriptionProperties(subscriptionId: String)

    /** GET /user/by/{aliasLabel}/{aliasId}/iams **/
    fun listIAMsByUser(aliasLabel: String, aliasId: String)
    /** GET /subscription/{subscriptionId}/iams **/
    fun listIAMsBySubscription(subscriptionId: String)

    /** EXISTING ENDPOINTS **/
    /** GET players/{playerId}?app_id={appId} **/
    fun getSubscription()
    /** PUT players/{playerId} **/
    fun updateSubscription()

    /** POST players/{playerId}/email_logout **/
    fun logoutEmail()

    /** POST players/{playerId}/on_purchase **/
    fun createPurchase()

    /** POST players **/
    fun createSession()

    /** POST players/{playerId}/on_session **/
    fun updateSession()

    /** POST players/{playerId}/on_focus **/
    fun createFocus()

    /** POST notifications **/
    fun createNotification()

    /** PUT notifications/{id} **/
    fun updateNotificationAsOpened()

    /** PUT notifications/{id}/report_received **/
    fun updateNotificationAsReceived()

    /** POST in_app_messages/{messageId}/click **/
    fun updateIAMAsClicked()

    /** POST in_app_messages/{messageId}/pageImpression **/
    fun updateIAMAsPageImpression()

    /** POST in_app_messages/{messageId}/impression **/
    fun updateIAMAsImpression()

    /** GET in_app_messages/device_preview?preview_id={previewUUID}&app_id={appId} **/
    fun getIAMPreviewData()

    /** GET in_app_messages/{messageId}/variants/{variantId}/html?app_id={appId} **/
    fun getIAMData()

    /** GET apps/{appId}/android_params.js?player_id={userId} **/
    fun getParams()
}
