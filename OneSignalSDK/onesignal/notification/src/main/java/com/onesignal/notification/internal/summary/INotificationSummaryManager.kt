package com.onesignal.notification.internal.summary

internal interface INotificationSummaryManager {
    // A notification was just dismissed, check if it was a child to a summary notification and update it.
    suspend fun updatePossibleDependentSummaryOnDismiss(androidNotificationId: Int)

    // Called from an opened / dismissed / cancel event of a single notification to update it's parent the summary notification.
    suspend fun updateSummaryNotificationAfterChildRemoved(group: String, dismissed: Boolean)

    /**
     * Clears notifications from the status bar based on a few parameters
     */
    suspend fun clearNotificationOnSummaryClick(group: String)
}
