package com.onesignal;

import java.util.Arrays;
import java.util.HashSet;

class OSTaskRemoteController extends OSTaskController {

    // Available task for delay
    static final String GET_TAGS = "getTags()";
    static final String SET_SMS_NUMBER = "setSMSNumber()";
    static final String SET_EMAIL = "setEmail()";
    static final String LOGOUT_SMS_NUMBER = "logoutSMSNumber()";
    static final String LOGOUT_EMAIL = "logoutEmail()";
    static final String SYNC_HASHED_EMAIL = "syncHashedEmail()";
    static final String SET_EXTERNAL_USER_ID = "setExternalUserId()";
    static final String SET_LANGUAGE = "setLanguage()";
    static final String SET_SUBSCRIPTION = "setSubscription()";
    static final String PROMPT_LOCATION = "promptLocation()";
    static final String IDS_AVAILABLE = "idsAvailable()";
    static final String SEND_TAG = "sendTag()";
    static final String SEND_TAGS = "sendTags()";
    static final String SET_LOCATION_SHARED = "setLocationShared()";
    static final String SET_DISABLE_GMS_MISSING_PROMPT = "setDisableGMSMissingPrompt()";
    static final String SET_REQUIRES_USER_PRIVACY_CONSENT = "setRequiresUserPrivacyConsent()";
    static final String UNSUBSCRIBE_WHEN_NOTIFICATION_ARE_DISABLED = "unsubscribeWhenNotificationsAreDisabled()";
    static final String HANDLE_NOTIFICATION_OPEN = "handleNotificationOpen()";
    static final String CLEAR_NOTIFICATIONS = "clearOneSignalNotifications()";
    static final String REMOVE_GROUPED_NOTIFICATIONS = "removeGroupedNotifications()";
    static final String REMOVE_NOTIFICATION = "removeNotification()";
    static final String PAUSE_IN_APP_MESSAGES = "pauseInAppMessages()";
    static final String SET_IN_APP_MESSAGE_LIFECYCLE_HANDLER = "setInAppMessageLifecycleHandler()";
    static final String APP_LOST_FOCUS = "onAppLostFocus()";
    static final String SEND_OUTCOME = "sendOutcome()";
    static final String SEND_UNIQUE_OUTCOME = "sendUniqueOutcome()";
    static final String SEND_OUTCOME_WITH_VALUE = "sendOutcomeWithValue()";
    static final HashSet<String> METHODS_AVAILABLE_FOR_DELAY = new HashSet<>(Arrays.asList(
            GET_TAGS,
            SET_SMS_NUMBER,
            SET_EMAIL,
            LOGOUT_SMS_NUMBER,
            LOGOUT_EMAIL,
            SYNC_HASHED_EMAIL,
            SET_EXTERNAL_USER_ID,
            SET_LANGUAGE,
            SET_SUBSCRIPTION,
            PROMPT_LOCATION,
            IDS_AVAILABLE,
            SEND_TAG,
            SEND_TAGS,
            SET_LOCATION_SHARED,
            SET_DISABLE_GMS_MISSING_PROMPT,
            SET_REQUIRES_USER_PRIVACY_CONSENT,
            UNSUBSCRIBE_WHEN_NOTIFICATION_ARE_DISABLED,
            HANDLE_NOTIFICATION_OPEN,
            APP_LOST_FOCUS,
            SEND_OUTCOME,
            SEND_UNIQUE_OUTCOME,
            SEND_OUTCOME_WITH_VALUE,
            REMOVE_GROUPED_NOTIFICATIONS,
            REMOVE_NOTIFICATION,
            CLEAR_NOTIFICATIONS
    ));

    private final OSRemoteParamController paramController;

    OSTaskRemoteController(OSRemoteParamController paramController, OSLogger logger) {
        super(logger);
        this.paramController = paramController;
    }

    /**
     * Check if task should be queue
     *
     * @return true if remote params aren't available and current method needs them otherwise false
     * */
    boolean shouldQueueTaskForInit(String task) {
        return !paramController.isRemoteParamsCallDone() && METHODS_AVAILABLE_FOR_DELAY.contains(task);
    }

}
