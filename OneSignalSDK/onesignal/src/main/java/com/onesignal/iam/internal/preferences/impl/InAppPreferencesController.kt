package com.onesignal.iam.internal.preferences.impl

import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.iam.internal.preferences.IInAppPreferencesController
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class InAppPreferencesController(
    private val _prefs: IPreferencesService
) : IInAppPreferencesController {
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
    override fun cleanInAppMessageIds(oldMessageIds: Set<String>?) {
        if (oldMessageIds != null && oldMessageIds.isNotEmpty()) {
            val dismissedMessages: Set<String>? = _prefs.getStringSet(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_DISMISSED_IAMS, null)
            val impressionedMessages: Set<String>? = _prefs.getStringSet(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_IMPRESSIONED_IAMS, null)

            if (dismissedMessages != null && dismissedMessages.isNotEmpty()) {
                val mutDismissedMessages = dismissedMessages.toMutableSet()
                mutDismissedMessages.removeAll(oldMessageIds)
                _prefs.saveStringSet(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_DISMISSED_IAMS, mutDismissedMessages)
            }

            if (impressionedMessages != null && impressionedMessages.isNotEmpty()) {
                val mutImpressionedMessages = impressionedMessages.toMutableSet()
                mutImpressionedMessages.removeAll(oldMessageIds)
                _prefs.saveStringSet(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_IMPRESSIONED_IAMS, mutImpressionedMessages)
            }
        }
    }

    override fun cleanInAppMessageClickedClickIds(oldClickedClickIds: Set<String>?) {
        if (oldClickedClickIds != null && oldClickedClickIds.isNotEmpty()) {
            val clickedClickIds: Set<String>? = _prefs.getStringSet(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_CLICKED_CLICK_IDS_IAMS, null)
            if (clickedClickIds != null && clickedClickIds.isNotEmpty()) {
                val mutclickedClickIds = clickedClickIds.toMutableSet()
                mutclickedClickIds.removeAll(oldClickedClickIds)
                _prefs.saveStringSet(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_CLICKED_CLICK_IDS_IAMS, mutclickedClickIds)
            }
        }
    }

    override var clickedMessagesId: Set<String>?
        get() = _prefs.getStringSet(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_CLICKED_CLICK_IDS_IAMS, null)
        set(value) = _prefs.saveStringSet(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_CLICKED_CLICK_IDS_IAMS, value)

    override var impressionesMessagesId: Set<String>?
        get() = _prefs.getStringSet(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_IMPRESSIONED_IAMS, null)
        set(value) = _prefs.saveStringSet(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_IMPRESSIONED_IAMS, value)

    override var viewPageImpressionedIds: Set<String>?
        get() = _prefs.getStringSet(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_PAGE_IMPRESSIONED_IAMS, null)
        set(value) = _prefs.saveStringSet(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_PAGE_IMPRESSIONED_IAMS, value)

    override var dismissedMessagesId: Set<String>?
        get() = _prefs.getStringSet(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_DISMISSED_IAMS, null)
        set(value) = _prefs.saveStringSet(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_DISMISSED_IAMS, value)

    override var savedIAMs: String?
        get() = _prefs.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_CACHED_IAMS, null)
        set(value) = _prefs.saveString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_CACHED_IAMS, value)

    // This pattern matches the pattern used by the Date class's toString() method
    override var lastTimeInAppDismissed: Long?
        get() = _prefs.getLong(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_LAST_TIME_IAM_DISMISSED, null)
        set(value) = _prefs.saveLong(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_LAST_TIME_IAM_DISMISSED, value)
}
