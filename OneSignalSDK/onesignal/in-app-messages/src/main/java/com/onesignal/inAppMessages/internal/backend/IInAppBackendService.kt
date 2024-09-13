package com.onesignal.inAppMessages.internal.backend

import com.onesignal.common.exceptions.BackendException
import com.onesignal.inAppMessages.internal.InAppMessage
import com.onesignal.inAppMessages.internal.InAppMessageContent

/**
 * This backend service provides access to the In App Message endpoints
 */
internal interface IInAppBackendService {
    /**
     * List the in app messages for a specific [appId]/[subscriptionId].
     *
     * @param appId The ID of the application that the IAM will be retrieved from.
     * @param subscriptionId The specific subscription within the [appId] the IAM will be delivered to.
     * @param jwt The JWT token for the current logged in user. Not used if identity verification is off.
     *
     * @return The list of IAMs associated to the subscription, or null if the IAMs could not be retrieved.
     */
    suspend fun listInAppMessages(
        appId: String,
        subscriptionId: String,
        jwt: String? = null,
    ): List<InAppMessage>?

    /**
     * Retrieve the data for a specific In App Message.
     *
     * @param appId The ID of the application that the IAM will be retrieved from.
     * @param messageId The ID of the IAM that should be retrieved.
     * @param variantId The optional ID of the variant that should be retrieved. If not specified,
     * the default variant will be used.
     */
    suspend fun getIAMData(
        appId: String,
        messageId: String,
        variantId: String?,
    ): GetIAMDataResponse

    /**
     * Retrieve the preview data for a specific In App Message.
     *
     * @param appId The ID of the application that the IAM will be retrieved from.
     * @param previewUUID THe ID of the preview IAM that should be retrieved.
     */
    suspend fun getIAMPreviewData(
        appId: String,
        previewUUID: String,
    ): InAppMessageContent?

    /**
     * Indicate an IAM was clicked on by the user.
     *
     * If there is a non-successful response from the backend, a [BackendException] will be thrown with response data.
     *
     * @param appId The ID of the application the IAM came from.
     * @param subscriptionId The ID of the subscription the IAM was delivered to.
     * @param variantId The ID of the variant that was shown to the user.
     * @param messageId The ID of the message that was shown to the user.
     * @param clickId The optional ID of the element that was clicked on by the user. If not specified, TODO: What does it mean to not have a click ID?
     * @param isFirstClick Whether this was the first click on the IAM.
     */
    suspend fun sendIAMClick(
        appId: String,
        subscriptionId: String,
        variantId: String?,
        messageId: String,
        clickId: String?,
        isFirstClick: Boolean,
    )

    /**
     * Indicate an impression against an IAM.
     *
     * If there is a non-successful response from the backend, a [BackendException] will be thrown with response data.
     *
     * @param appId The ID of the application the IAM came from.
     * @param subscriptionId The ID of the subscription the IAM was delivered to.
     * @param variantId The ID of the variant that was shown to the user.
     * @param messageId The ID of the message that was shown to the user.
     */
    suspend fun sendIAMImpression(
        appId: String,
        subscriptionId: String,
        variantId: String?,
        messageId: String,
    )

    /**
     * Indicate an impression against an IAM page.
     *
     * If there is a non-successful response from the backend, a [BackendException] will be thrown with response data.
     *
     * @param appId The ID of the application the IAM came from.
     * @param subscriptionId The ID of the subscription the IAM was delivered to.
     * @param variantId The ID of the variant that was shown to the user.
     * @param messageId The ID of the message that was shown to the user.
     * @param pageId The ID of the page that was shown to the user.
     */
    suspend fun sendIAMPageImpression(
        appId: String,
        subscriptionId: String,
        variantId: String?,
        messageId: String,
        pageId: String?,
    )
}

internal class GetIAMDataResponse(
    /**
     * The content, when the response is successful
     */
    val content: InAppMessageContent?,
    /**
     * Whether the call should be retried, when [content] is null
     */
    val shouldRetry: Boolean,
)
