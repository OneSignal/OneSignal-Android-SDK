package com.onesignal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.onesignal.OSDynamicTriggerController.OSDynamicTriggerControllerObserver;
import com.onesignal.OneSignalRestClient.ResponseHandler;
import com.onesignal.language.LanguageContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class OSInAppMessageController implements OSDynamicTriggerControllerObserver, OSSystemConditionController.OSSystemConditionObserver {

    private static ArrayList<String> PREFERRED_VARIANT_ORDER = new ArrayList<String>() {{
        add("android");
        add("app");
        add("all");
    }};

    public static final String IN_APP_MESSAGES_JSON_KEY = "in_app_messages";
    private static final String OS_SAVE_IN_APP_MESSAGE = "OS_SAVE_IN_APP_MESSAGE";
    private static final Object LOCK = new Object();
    private final LanguageContext languageContext;

    OSTriggerController triggerController;
    private OSSystemConditionController systemConditionController;
    private OSInAppMessageRepository inAppMessageRepository;

    // IAMs loaded remotely from on_session
    //   If on_session won't be called this will be loaded from cache
    @NonNull
    private ArrayList<OSInAppMessage> messages;
    // IAMs that have been dismissed by the user
    //   This mean they have already displayed to the user
    @NonNull
    final private Set<String> dismissedMessages;
    // IAMs that have been displayed to the user
    //   This means their impression has been successfully posted to our backend and should not be counted again
    @NonNull
    final private Set<String> impressionedMessages;
    // IAM clicks that have been successfully posted to our backend and should not be counted again
    @NonNull
    final private Set<String> clickedClickIds;
    // Ordered IAMs queued to display, includes the message currently displaying, if any.
    @NonNull
    final private ArrayList<OSInAppMessage> messageDisplayQueue;
    // IAMs displayed with last displayed time and quantity of displays data
    // This is retrieved from a DB Table that take care of each object to be unique
    @NonNull
    private List<OSInAppMessage> redisplayedInAppMessages;

    private OSInAppMessagePrompt currentPrompt = null;
    private boolean inAppMessagingEnabled = true;
    private boolean inAppMessageShowing = false;

    @Nullable
    Date lastTimeInAppDismissed = null;
    private int htmlNetworkRequestAttemptCount = 0;

    protected OSInAppMessageController(OneSignalDbHelper dbHelper, LanguageContext languageContext) {
        messages = new ArrayList<>();
        dismissedMessages = OSUtils.newConcurrentSet();
        messageDisplayQueue = new ArrayList<>();
        impressionedMessages = OSUtils.newConcurrentSet();
        clickedClickIds = OSUtils.newConcurrentSet();
        triggerController = new OSTriggerController(this);
        systemConditionController = new OSSystemConditionController(this);
        this.languageContext = languageContext;

        Set<String> tempDismissedSet = OneSignalPrefs.getStringSet(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_DISMISSED_IAMS,
                null
        );
        if (tempDismissedSet != null)
            dismissedMessages.addAll(tempDismissedSet);

        Set<String> tempImpressionsSet = OneSignalPrefs.getStringSet(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_IMPRESSIONED_IAMS,
                null
        );
        if (tempImpressionsSet != null)
            impressionedMessages.addAll(tempImpressionsSet);

        Set<String> tempClickedMessageIdsSet = OneSignalPrefs.getStringSet(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_CLICKED_CLICK_IDS_IAMS,
                null
        );
        if (tempClickedMessageIdsSet != null)
            clickedClickIds.addAll(tempClickedMessageIdsSet);

        initRedisplayData(dbHelper);
    }

    OSInAppMessageRepository getInAppMessageRepository(OneSignalDbHelper dbHelper) {
        if (inAppMessageRepository == null)
            inAppMessageRepository = new OSInAppMessageRepository(dbHelper);

        return inAppMessageRepository;
    }

    protected void initRedisplayData(OneSignalDbHelper dbHelper) {
        inAppMessageRepository = getInAppMessageRepository(dbHelper);
        redisplayedInAppMessages = inAppMessageRepository.getCachedInAppMessages();

        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "redisplayedInAppMessages: " + redisplayedInAppMessages.toString());
    }

    void resetSessionLaunchTime() {
        OSDynamicTriggerController.resetSessionLaunchTime();
    }

    // Normally we wait until on_session call to download the latest IAMs
    //    however an on session won't happen
    void initWithCachedInAppMessages() {
        // Do not reload from cache if already loaded.
        if (!messages.isEmpty()) {
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "initWithCachedInAppMessages with already in memory messages: " + messages);
            return;
        }

        String cachedInAppMessageString = OneSignalPrefs.getString(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_CACHED_IAMS,
                null
        );
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "initWithCachedInAppMessages: " + cachedInAppMessageString);

        if (cachedInAppMessageString == null || cachedInAppMessageString.isEmpty())
            return;

        synchronized (LOCK) {
            try {
                // Second check to avoid getting the lock while message list is being set
                if (!messages.isEmpty())
                    return;

                processInAppMessageJson(new JSONArray(cachedInAppMessageString));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Called after the device is registered from UserStateSynchronizer
     * which is the REST call to create the player record on_session
     */
    void receivedInAppMessageJson(@NonNull JSONArray json) throws JSONException {
        // Cache copy for quick cold starts
        OneSignalPrefs.saveString(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_CACHED_IAMS,
                json.toString());

        resetRedisplayMessagesBySession();

        processInAppMessageJson(json);
    }

    private void resetRedisplayMessagesBySession() {
        for (OSInAppMessage redisplayInAppMessage : redisplayedInAppMessages) {
            redisplayInAppMessage.setDisplayedInSession(false);
        }
    }

    private void processInAppMessageJson(@NonNull JSONArray json) throws JSONException {
        synchronized (LOCK) {
            ArrayList<OSInAppMessage> newMessages = new ArrayList<>();
            for (int i = 0; i < json.length(); i++) {
                JSONObject messageJson = json.getJSONObject(i);
                OSInAppMessage message = new OSInAppMessage(messageJson);

                newMessages.add(message);
            }

            messages = newMessages;
        }

        evaluateInAppMessages();
    }

    private void evaluateInAppMessages() {
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Starting evaluateInAppMessages");

        for (OSInAppMessage message : messages) {
            // Make trigger evaluation first, dynamic trigger might change "trigger changed" flag value for redisplay messages
            if (triggerController.evaluateMessageTriggers(message)) {
                setDataForRedisplay(message);

                if (!dismissedMessages.contains(message.messageId) && !message.isFinished()) {
                    queueMessageForDisplay(message);
                }
            }
        }
    }

    private @Nullable String variantIdForMessage(@NonNull OSInAppMessage message) {
        String language = languageContext.getLanguage();

        for (String variant : PREFERRED_VARIANT_ORDER) {
            if (!message.variants.containsKey(variant))
                continue;

            HashMap<String, String> variantMap = message.variants.get(variant);
            if (variantMap.containsKey(language))
                return variantMap.get(language);
            return variantMap.get("default");
        }

        return null;
    }

    private static void printHttpSuccessForInAppMessageRequest(String requestType, String response) {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "Successful post for in-app message " + requestType + " request: " + response);
    }

    private static void printHttpErrorForInAppMessageRequest(String requestType, int statusCode, String response) {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, "Encountered a " + statusCode + " error while attempting in-app message " + requestType + " request: " + response);
    }

    void onMessageWasShown(@NonNull final OSInAppMessage message) {
        if (message.isPreview)
            return;

        // Check that the messageId is in impressionedMessages so we return early without a second post being made
        if (impressionedMessages.contains(message.messageId))
            return;

        // Add the messageId to impressionedMessages so no second request is made
        impressionedMessages.add(message.messageId);

        final String variantId = variantIdForMessage(message);
        if (variantId == null)
            return;

        try {
            JSONObject json = new JSONObject() {{
                put("app_id", OneSignal.appId);
                put("player_id", OneSignal.getUserId());
                put("variant_id", variantId);
                put("device_type", new OSUtils().getDeviceType());
                put("first_impression", true);
            }};

            OneSignalRestClient.post("in_app_messages/" + message.messageId + "/impression", json, new ResponseHandler() {
                @Override
                void onSuccess(String response) {
                    printHttpSuccessForInAppMessageRequest("impression", response);
                    OneSignalPrefs.saveStringSet(
                            OneSignalPrefs.PREFS_ONESIGNAL,
                            OneSignalPrefs.PREFS_OS_IMPRESSIONED_IAMS,
                            // Post success, store impressioned messageId to disk
                            impressionedMessages);
                }

                @Override
                void onFailure(int statusCode, String response, Throwable throwable) {
                    printHttpErrorForInAppMessageRequest("impression", statusCode, response);
                    // Post failed, impressionedMessage should be removed and this way another post can be attempted
                    impressionedMessages.remove(message.messageId);
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, "Unable to execute in-app message impression HTTP request due to invalid JSON");
        }
    }

    void onMessageActionOccurredOnMessage(@NonNull final OSInAppMessage message, @NonNull final JSONObject actionJson) throws JSONException {
        final OSInAppMessageAction action = new OSInAppMessageAction(actionJson);
        action.firstClick = message.takeActionAsUnique();

        firePublicClickHandler(message.messageId, action);
        beginProcessingPrompts(message, action.prompts);
        fireClickAction(action);
        fireRESTCallForClick(message, action);
        fireTagCallForClick(action);
        fireOutcomesForClick(message.messageId, action.outcomes);
    }

    void onMessageActionOccurredOnPreview(@NonNull final OSInAppMessage message, @NonNull final JSONObject actionJson) throws JSONException {
        final OSInAppMessageAction action = new OSInAppMessageAction(actionJson);
        action.firstClick = message.takeActionAsUnique();

        firePublicClickHandler(message.messageId, action);
        beginProcessingPrompts(message, action.prompts);
        fireClickAction(action);
        logInAppMessagePreviewActions(action);
    }

    private void logInAppMessagePreviewActions(final OSInAppMessageAction action) {
        if (action.tags != null)
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "Tags detected inside of the action click payload, ignoring because action came from IAM preview:: " + action.tags.toString());

        if (action.outcomes.size() > 0)
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "Outcomes detected inside of the action click payload, ignoring because action came from IAM preview: " + action.outcomes.toString());

        // TODO: Add more action payload preview logs here in future
    }

    private void beginProcessingPrompts(OSInAppMessage message, final List<OSInAppMessagePrompt> prompts) {
        if (prompts.size() > 0) {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "IAM showing prompts from IAM: " + message.toString());
            // TODO until we don't fix the activity going forward or back dismissing the IAM, we need to auto dismiss
            WebViewManager.dismissCurrentInAppMessage();
            showMultiplePrompts(message, prompts);
        }
    }

    private void showMultiplePrompts(final OSInAppMessage inAppMessage, final List<OSInAppMessagePrompt> prompts) {
        for (OSInAppMessagePrompt prompt : prompts) {
            // Don't show prompt twice
            if (!prompt.hasPrompted()) {
                currentPrompt = prompt;
                break;
            }
        }

        if (currentPrompt != null) {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "IAM prompt to handle: " + currentPrompt.toString());
            currentPrompt.setPrompted(true);
            currentPrompt.handlePrompt(new OneSignal.OSPromptActionCompletionCallback() {
                @Override
                public void onCompleted(OneSignal.PromptActionResult result) {
                    currentPrompt = null;
                    OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "IAM prompt to handle finished with result: " + result);

                    // On preview mode we show informative alert dialogs
                    if (inAppMessage.isPreview && result == OneSignal.PromptActionResult.LOCATION_PERMISSIONS_MISSING_MANIFEST)
                        showAlertDialogMessage(inAppMessage, prompts);
                    else
                        showMultiplePrompts(inAppMessage, prompts);
                }
            });
        } else {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "No IAM prompt to handle, dismiss message: " + inAppMessage.messageId);
            messageWasDismissed(inAppMessage);
        }
    }

    private void showAlertDialogMessage(final OSInAppMessage inAppMessage, final List<OSInAppMessagePrompt> prompts) {
        final String messageTitle = OneSignal.appContext.getString(R.string.location_not_available_title);
        final String message = OneSignal.appContext.getString(R.string.location_not_available_message);
        Activity currentActivity = OneSignal.getCurrentActivity();
        new AlertDialog.Builder(currentActivity)
                .setTitle(messageTitle)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        showMultiplePrompts(inAppMessage, prompts);
                    }
                })
                .show();
    }

    private void fireOutcomesForClick(String messageId, @NonNull final List<OSInAppMessageOutcome> outcomes) {
        OneSignal.getSessionManager().onDirectInfluenceFromIAMClick(messageId);
        OneSignal.sendClickActionOutcomes(outcomes);
    }

    private void fireTagCallForClick(@NonNull final OSInAppMessageAction action) {
        if (action.tags != null) {
            OSInAppMessageTag tags = action.tags;

            if (tags.getTagsToAdd() != null)
                OneSignal.sendTags(tags.getTagsToAdd());
            if (tags.getTagsToRemove() != null)
                OneSignal.deleteTags(tags.getTagsToRemove(), null);
        }
    }

    private void firePublicClickHandler(@NonNull final String messageId, @NonNull final OSInAppMessageAction action) {
        if (OneSignal.mInitBuilder.mInAppMessageClickHandler == null)
            return;

        OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
                // Send public outcome from handler
                // Send public outcome not from handler
                // Check that only on the handler
                // Any outcome sent on this callback should count as DIRECT from this IAM
                OneSignal.getSessionManager().onDirectInfluenceFromIAMClick(messageId);
                OneSignal.mInitBuilder.mInAppMessageClickHandler.inAppMessageClicked(action);
            }
        });
    }

    private void fireClickAction(@NonNull final OSInAppMessageAction action) {
        if (action.clickUrl != null && !action.clickUrl.isEmpty()) {
            if (action.urlTarget == OSInAppMessageAction.OSInAppMessageActionUrlType.BROWSER)
                OSUtils.openURLInBrowser(action.clickUrl);
            else if (action.urlTarget == OSInAppMessageAction.OSInAppMessageActionUrlType.IN_APP_WEBVIEW)
                OneSignalChromeTab.open(action.clickUrl, true);
        }
    }

    private void fireRESTCallForClick(@NonNull final OSInAppMessage message, @NonNull final OSInAppMessageAction action) {
        final String variantId = variantIdForMessage(message);
        if (variantId == null)
            return;

        final String clickId = action.clickId;
        // If IAM has redisplay the clickId may be available
        boolean clickAvailableByRedisplay = message.getRedisplayStats().isRedisplayEnabled() && message.isClickAvailable(clickId);

        // Never count multiple clicks for the same click UUID unless that click is from an IAM with redisplay
        if (!clickAvailableByRedisplay && clickedClickIds.contains(clickId))
            return;

        clickedClickIds.add(clickId);
        // Track clickId per IAM
        message.addClickId(clickId);

        try {
            JSONObject json = new JSONObject() {{
                put("app_id", OneSignal.getSavedAppId());
                put("device_type", new OSUtils().getDeviceType());
                put("player_id", OneSignal.getUserId());
                put("click_id", clickId);
                put("variant_id", variantId);
                if (action.firstClick)
                    put("first_click", true);
            }};

            OneSignalRestClient.post("in_app_messages/" + message.messageId + "/click", json, new ResponseHandler() {
                @Override
                void onSuccess(String response) {
                    printHttpSuccessForInAppMessageRequest("engagement", response);
                    // Persist success click to disk. Id already added to set before making the network call
                    OneSignalPrefs.saveStringSet(
                            OneSignalPrefs.PREFS_ONESIGNAL,
                            OneSignalPrefs.PREFS_OS_CLICKED_CLICK_IDS_IAMS,
                            clickedClickIds
                    );
                }

                @Override
                void onFailure(int statusCode, String response, Throwable throwable) {
                    printHttpErrorForInAppMessageRequest("engagement", statusCode, response);
                    clickedClickIds.remove(action.clickId);
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, "Unable to execute in-app message action HTTP request due to invalid JSON");
        }
    }

    /**
     * Part of redisplay logic
     * <p>
     * In order to redisplay an IAM, the following conditions must be satisfied:
     * 1. IAM has redisplay property
     * 2. Time delay between redisplay satisfied
     * 3. Has more redisplays
     * 4. An IAM trigger was satisfied
     * <p>
     * For redisplay, the message need to be removed from the arrays that track the display/impression
     * For click counting, every message has it click id array
     */
    private void setDataForRedisplay(OSInAppMessage message) {
        boolean messageDismissed = dismissedMessages.contains(message.messageId);
        int index = redisplayedInAppMessages.indexOf(message);

        if (messageDismissed && index != -1) {
            OSInAppMessage savedIAM = redisplayedInAppMessages.get(index);
            message.getRedisplayStats().setDisplayStats(savedIAM.getRedisplayStats());
            message.setDisplayedInSession(savedIAM.isDisplayedInSession());

            boolean triggerHasChanged = hasMessageTriggerChanged(message);
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "setDataForRedisplay: " + message.toString() + " triggerHasChanged: " + triggerHasChanged);

            // Check if conditions are correct for redisplay
            if (triggerHasChanged &&
                    message.getRedisplayStats().isDelayTimeSatisfied() &&
                    message.getRedisplayStats().shouldDisplayAgain()) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "setDataForRedisplay message available for redisplay: " + message.messageId);

                dismissedMessages.remove(message.messageId);
                impressionedMessages.remove(message.messageId);
                message.clearClickIds();
            }
        }
    }

    private boolean hasMessageTriggerChanged(OSInAppMessage message) {
        // Message that only have dynamic trigger should display only once per session
        boolean messageHasOnlyDynamicTrigger = triggerController.messageHasOnlyDynamicTriggers(message);
        if (messageHasOnlyDynamicTrigger)
            return !message.isDisplayedInSession();

        // Message that don't have triggers should display only once per session
        boolean shouldMessageDisplayInSession = !message.isDisplayedInSession() && message.triggers.isEmpty();

        return message.isTriggerChanged() || shouldMessageDisplayInSession;
    }

    /**
     * Message has passed triggers and de-duplication logic.
     * Display message now or add it to the queue to be displayed.
     */
    private void queueMessageForDisplay(@NonNull OSInAppMessage message) {
        synchronized (messageDisplayQueue) {
            // Make sure no message is ever added to the queue more than once
            if (!messageDisplayQueue.contains(message)) {
                messageDisplayQueue.add(message);
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "In app message with id, " + message.messageId + ", added to the queue");
            }

            attemptToShowInAppMessage();
        }
    }

    private void attemptToShowInAppMessage() {
        synchronized (messageDisplayQueue) {
            // We need to wait for system conditions to be the correct ones
            if (!systemConditionController.systemConditionsAvailable()) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.WARN, "In app message not showing due to system condition not correct");
                return;
            }

            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "displayFirstIAMOnQueue: " + messageDisplayQueue);
            // If there are IAMs in the queue and nothing showing, show first in the queue
            if (messageDisplayQueue.size() > 0 && !isInAppMessageShowing()) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "No IAM showing currently, showing first item in the queue!");
                displayMessage(messageDisplayQueue.get(0));
                return;
            }

            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "In app message is currently showing or there are no IAMs left in the queue!");
        }
    }

    boolean isInAppMessageShowing() {
        return inAppMessageShowing;
    }

    @Nullable
    OSInAppMessage getCurrentDisplayedInAppMessage() {
        // When in app messaging is paused, the messageDisplayQueue might have IAMs, so return null
        return inAppMessageShowing ? messageDisplayQueue.get(0) : null;
    }

    /**
     * Called after an In-App message is closed and it's dismiss animation has completed
     */
    void messageWasDismissed(@NonNull OSInAppMessage message) {
        messageWasDismissed(message, false);
    }

    void messageWasDismissed(@NonNull OSInAppMessage message, boolean failed) {
        if (!message.isPreview) {
            dismissedMessages.add(message.messageId);
            // If failed we will retry on next session
            if (!failed) {
                OneSignalPrefs.saveStringSet(
                        OneSignalPrefs.PREFS_ONESIGNAL,
                        OneSignalPrefs.PREFS_OS_DISMISSED_IAMS,
                        dismissedMessages);

                // Don't keep track of last displayed time for a preview
                lastTimeInAppDismissed = new Date();
                // Only increase IAM display quantity if IAM was truly displayed
                persistInAppMessage(message);
            }
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "OSInAppMessageController messageWasDismissed dismissedMessages: " + dismissedMessages.toString());
        }

        dismissCurrentMessage(message);
    }

    void messageWasDismissedByBackPress(@NonNull OSInAppMessage message) {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "OSInAppMessageController messageWasDismissed by back press: " + message.toString());
        // IAM was not dismissed by user, will be redisplay again until user dismiss it
        dismissCurrentMessage(message);
    }

    /**
     * Removes first item from the queue and attempts to show the next IAM in the queue
     *
     * @param message The message dismissed, preview messages are null
     */
    private void dismissCurrentMessage(@Nullable OSInAppMessage message) {
        // Remove DIRECT influence due to ClickHandler of ClickAction outcomes
        OneSignal.getSessionManager().onDirectInfluenceFromIAMClickFinished();

        if (currentPrompt != null) {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "Stop evaluateMessageDisplayQueue because prompt is currently displayed");
            return;
        }

        inAppMessageShowing = false;
        synchronized (messageDisplayQueue) {
            if (messageDisplayQueue.size() > 0) {
                if (message != null && !messageDisplayQueue.contains(message)) {
                    OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "Message already removed from the queue!");
                    return;
                } else {
                    String removedMessageId = messageDisplayQueue.remove(0).messageId;
                    OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "In app message with id, " + removedMessageId + ", dismissed (removed) from the queue!");
                }
            }

            // Display the next message in the queue, or attempt to add more IAMs to the queue
            if (messageDisplayQueue.size() > 0) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "In app message on queue available: " + messageDisplayQueue.get(0).messageId);
                displayMessage(messageDisplayQueue.get(0));
            } else {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "In app message dismissed evaluating messages");
                evaluateInAppMessages();
            }
        }
    }

    private void persistInAppMessage(final OSInAppMessage message) {
        long displayTimeSeconds = System.currentTimeMillis() / 1000;
        message.getRedisplayStats().setLastDisplayTime(displayTimeSeconds);
        message.getRedisplayStats().incrementDisplayQuantity();
        message.setTriggerChanged(false);
        message.setDisplayedInSession(true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);
                inAppMessageRepository.saveInAppMessage(message);
            }
        }, OS_SAVE_IN_APP_MESSAGE).start();

        // Update the data to enable future re displays
        // Avoid calling the repository data again
        int index = redisplayedInAppMessages.indexOf(message);
        if (index != -1) {
            redisplayedInAppMessages.set(index, message);
        } else {
            redisplayedInAppMessages.add(message);
        }

        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "persistInAppMessageForRedisplay: " + message.toString() + " with msg array data: " + redisplayedInAppMessages.toString());
    }

    private @Nullable
    String htmlPathForMessage(OSInAppMessage message) {
        String variantId = variantIdForMessage(message);

        if (variantId == null) {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, "Unable to find a variant for in-app message " + message.messageId);
            return null;
        }

        return "in_app_messages/" + message.messageId + "/variants/" + variantId + "/html?app_id=" + OneSignal.appId;
    }

    private void displayMessage(@NonNull final OSInAppMessage message) {
        if (!inAppMessagingEnabled) {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "In app messaging is currently paused, iam will not be shown!");
            return;
        }

        inAppMessageShowing = true;

        String htmlPath = htmlPathForMessage(message);
        OneSignalRestClient.get(htmlPath, new ResponseHandler() {
            @Override
            void onFailure(int statusCode, String response, Throwable throwable) {
                inAppMessageShowing = false;

                printHttpErrorForInAppMessageRequest("html", statusCode, response);

                if (!OSUtils.shouldRetryNetworkRequest(statusCode) || htmlNetworkRequestAttemptCount >= OSUtils.MAX_NETWORK_REQUEST_ATTEMPT_COUNT) {
                    // Failure limit reached, reset
                    htmlNetworkRequestAttemptCount = 0;
                    messageWasDismissed(message, true);
                    return;
                }

                // Failure limit not reached, increment by 1
                htmlNetworkRequestAttemptCount++;
                // Retry displaying the same IAM
                // Using the queueMessageForDisplay method follows safety checks to prevent issues
                // like having 2 IAMs showing at once or duplicate IAMs in the queue
                queueMessageForDisplay(message);
            }

            @Override
            void onSuccess(String response) {
                // Successful request, reset count
                htmlNetworkRequestAttemptCount = 0;

                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    String htmlStr = jsonResponse.getString("html");

                    double displayDuration = jsonResponse.optDouble("display_duration");
                    message.setDisplayDuration(displayDuration);

                    OneSignal.getSessionManager().onInAppMessageReceived(message.messageId);
                    WebViewManager.showHTMLString(message, htmlStr);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, null);
    }

    void displayPreviewMessage(@NonNull String previewUUID) {
        inAppMessageShowing = true;

        String htmlPath = "in_app_messages/device_preview?preview_id=" + previewUUID + "&app_id=" + OneSignal.appId;
        OneSignalRestClient.get(htmlPath, new ResponseHandler() {
            @Override
            void onFailure(int statusCode, String response, Throwable throwable) {
                printHttpErrorForInAppMessageRequest("html", statusCode, response);

                dismissCurrentMessage(null);
            }

            @Override
            void onSuccess(String response) {
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    String htmlStr = jsonResponse.getString("html");

                    OSInAppMessage message = new OSInAppMessage(true);

                    double displayDuration = jsonResponse.optDouble("display_duration");
                    message.setDisplayDuration(displayDuration);

                    WebViewManager.showHTMLString(message, htmlStr);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, null);
    }

    /**
     * Part of redisplay logic
     * <p>
     * Will update redisplay messages depending on dynamic triggers before setDataForRedisplay is called.
     * @see OSInAppMessageController#setDataForRedisplay(OSInAppMessage)
     *
     * We can't depend only on messageTriggerConditionChanged, due to trigger evaluation to true before scheduling
     * @see OSInAppMessageController#messageTriggerConditionChanged()
     */
    @Override
    public void messageDynamicTriggerCompleted(String triggerId) {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "messageDynamicTriggerCompleted called with triggerId: " + triggerId);
        Set<String> triggerIds = new HashSet<>();
        triggerIds.add(triggerId);
        makeRedisplayMessagesAvailableWithTriggers(triggerIds);
    }

    /**
     * Dynamic trigger logic
     * <p>
     * This will re evaluate messages due to dynamic triggers evaluating to true potentially
     *
     * @see OSInAppMessageController#setDataForRedisplay(OSInAppMessage)
     */
    @Override
    public void messageTriggerConditionChanged() {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "messageTriggerConditionChanged called");

        // This method is called when a time-based trigger timer fires, meaning the message can
        //  probably be shown now. So the current message conditions should be re-evaluated
        evaluateInAppMessages();
    }

    /**
     * If this method is called a system condition has changed to success
     * - Keyboard is down
     * - No DialogFragment visible
     * - Activity is on focus, this mean no prompt permissions visible
     */
    @Override
    public void systemConditionChanged() {
        attemptToShowInAppMessage();
    }

    /**
     * Part of redisplay logic
     * <p>
     * Make all messages with redisplay available if:
     * - Already displayed
     * - At least one Trigger has changed
     */
    private void makeRedisplayMessagesAvailableWithTriggers(Collection<String> newTriggersKeys) {
        for (OSInAppMessage message : messages) {
            if (!message.isTriggerChanged() && redisplayedInAppMessages.contains(message) &&
                    triggerController.isTriggerOnMessage(message, newTriggersKeys)) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "Trigger changed for message: " + message.toString());
                message.setTriggerChanged(true);
            }
        }
    }

    /**
     * Trigger logic
     * <p>
     * These methods mostly pass data to the Trigger Controller, but also cause the SDK to
     * re-evaluate messages to see if we should display/redisplay a message now that the trigger
     * conditions have changed.
     */
    void addTriggers(Map<String, Object> newTriggers) {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "Add trigger called with: " + newTriggers.toString());
        triggerController.addTriggers(newTriggers);
        makeRedisplayMessagesAvailableWithTriggers(newTriggers.keySet());
        evaluateInAppMessages();
    }

    void removeTriggersForKeys(Collection<String> keys) {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "Remove trigger called with keys: " + keys);
        triggerController.removeTriggersForKeys(keys);
        makeRedisplayMessagesAvailableWithTriggers(keys);
        evaluateInAppMessages();
    }

    void setInAppMessagingEnabled(boolean enabled) {
        inAppMessagingEnabled = enabled;
        if (enabled)
            evaluateInAppMessages();
    }

    @Nullable
    Object getTriggerValue(String key) {
        return triggerController.getTriggerValue(key);
    }

    @NonNull
    public ArrayList<OSInAppMessage> getInAppMessageDisplayQueue() {
        return messageDisplayQueue;
    }

    @NonNull
    public List<OSInAppMessage> getRedisplayedInAppMessages() {
        return redisplayedInAppMessages;
    }
}
