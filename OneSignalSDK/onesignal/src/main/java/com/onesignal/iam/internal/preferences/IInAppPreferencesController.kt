package com.onesignal.iam.internal.preferences

internal interface IInAppPreferencesController {
    var clickedMessagesId: Set<String>?
    var impressionesMessagesId: Set<String>?
    var viewPageImpressionedIds: Set<String>?
    var dismissedMessagesId: Set<String>?
    var savedIAMs: String?

    // This pattern matches the pattern used by the Date class's toString() method
    var lastTimeInAppDismissed: Long?

    /**
     * Clean up 6 month old IAM ids in [android.content.SharedPreferences]:
     * 1. Dismissed message ids
     * 2. Impressioned message ids
     * <br></br><br></br>
     * Note: This should only ever be called by [InAppPreferencesController.cleanCachedInAppMessages]
     * <br></br><br></br>
     *
     * @see InAppPreferencesController.cleanCachedInAppMessages
     */
    fun cleanInAppMessageIds(oldMessageIds: Set<String>?)
    fun cleanInAppMessageClickedClickIds(oldClickedClickIds: Set<String>?)
}
