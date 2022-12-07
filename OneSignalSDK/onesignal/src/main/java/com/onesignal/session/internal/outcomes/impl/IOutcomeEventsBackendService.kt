package com.onesignal.session.internal.outcomes.impl

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.subscriptions.ISubscription

/**
 * The backend service for outcomes.
 */
internal interface IOutcomeEventsBackendService {

    /**
     * Send an outcome event to the backend.
     *
     * If there is a non-successful response from the backend, a [BackendException] will be thrown with response data.
     *
     * @param appId The ID of the application this outcome event occurred under.
     * @param userId The OneSignal user ID that is active during the outcome event.
     * @param subscriptionId The subscription ID that is active during the outcome event.
     * @param direct Whether this outcome event is direct. `true` if it is, `false` if it isn't, `null` if should not be specified.
     * @param event The outcome event to send up.
     */
    suspend fun sendOutcomeEvent(appId: String, userId: String, subscriptionId: String, direct: Boolean?, event: OutcomeEvent)
}
