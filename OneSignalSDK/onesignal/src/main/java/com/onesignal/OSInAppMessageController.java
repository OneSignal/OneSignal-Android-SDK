package com.onesignal;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.onesignal.OSDynamicTriggerController.OSDynamicTriggerControllerObserver;
import com.onesignal.OneSignalRestClient.ResponseHandler;

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

    private static final Object LOCK = new Object();
    private static final String OS_SAVE_IN_APP_MESSAGE = "OS_SAVE_IN_APP_MESSAGE";
    public static final String IN_APP_MESSAGES_JSON_KEY = "in_app_messages";
    private static ArrayList<String> PREFERRED_VARIANT_ORDER = new ArrayList<String>() {{
        add("android");
        add("app");
        add("all");
    }};

    OSTriggerController triggerController;
    private OSSystemConditionController systemConditionController;
    private OSInAppMessageRepository inAppMessageRepository;
    private OSLogger logger;

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
    //   This means their impression has been successfully posted to our backend and should not be counted again
    @NonNull
    final private Set<String> viewedPageIds;
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

    protected OSInAppMessageController(OneSignalDbHelper dbHelper, OSLogger logger) {
        messages = new ArrayList<>();
        dismissedMessages = OSUtils.newConcurrentSet();
        messageDisplayQueue = new ArrayList<>();
        impressionedMessages = OSUtils.newConcurrentSet();
        viewedPageIds = OSUtils.newConcurrentSet();
        clickedClickIds = OSUtils.newConcurrentSet();
        triggerController = new OSTriggerController(this);
        systemConditionController = new OSSystemConditionController(this);
        this.logger = logger;

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

        Set<String> tempPageImpressionsSet = OneSignalPrefs.getStringSet(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_PAGE_IMPRESSIONED_IAMS,
                null
        );
        if (tempPageImpressionsSet != null)
            viewedPageIds.addAll(tempPageImpressionsSet);

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
        String languageIdentifier = OSUtils.getCorrectedLanguage();

        for (String variant : PREFERRED_VARIANT_ORDER) {
            if (!message.variants.containsKey(variant))
                continue;

            HashMap<String, String> variantMap = message.variants.get(variant);
            if (variantMap.containsKey(languageIdentifier))
                return variantMap.get(languageIdentifier);
            return variantMap.get("default");
        }

        return null;
    }

    private void printHttpSuccessForInAppMessageRequest(String requestType, String response) {
        logger.debug("Successful post for in-app message " + requestType + " request: " + response);
    }

    private void printHttpErrorForInAppMessageRequest(String requestType, int statusCode, String response) {
        logger.error("Encountered a " + statusCode + " error while attempting in-app message " + requestType + " request: " + response);
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

    void onPageChanged(@NonNull final OSInAppMessage message, @NonNull final JSONObject eventJson) {
        final OSInAppMessagePage newPage = new OSInAppMessagePage(eventJson);
        if (message.isPreview) {
            return;
        }
        fireRESTCallForPageChange(message, newPage);
    }

    void onMessageActionOccurredOnMessage(@NonNull final OSInAppMessage message, @NonNull final JSONObject actionJson) throws JSONException {
        final OSInAppMessageAction action = new OSInAppMessageAction(actionJson);
        action.setFirstClick(message.takeActionAsUnique());

        firePublicClickHandler(message.messageId, action);
        beginProcessingPrompts(message, action.getPrompts());
        fireClickAction(action);
        fireRESTCallForClick(message, action);
        fireTagCallForClick(action);
        fireOutcomesForClick(message.messageId, action.getOutcomes());
    }

    void onMessageActionOccurredOnPreview(@NonNull final OSInAppMessage message, @NonNull final JSONObject actionJson) throws JSONException {
        final OSInAppMessageAction action = new OSInAppMessageAction(actionJson);
        action.setFirstClick(message.takeActionAsUnique());

        firePublicClickHandler(message.messageId, action);
        beginProcessingPrompts(message, action.getPrompts());
        fireClickAction(action);
        logInAppMessagePreviewActions(action);
    }

    private void logInAppMessagePreviewActions(final OSInAppMessageAction action) {
        if (action.getTags() != null)
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "Tags detected inside of the action click payload, ignoring because action came from IAM preview:: " + action.getTags().toString());

        if (action.getOutcomes().size() > 0)
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "Outcomes detected inside of the action click payload, ignoring because action came from IAM preview: " + action.getOutcomes().toString());

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
        new AlertDialog.Builder(OneSignal.getCurrentActivity())
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
        if (action.getTags() != null) {
            OSInAppMessageTag tags = action.getTags();

            if (tags.getTagsToAdd() != null)
                OneSignal.sendTags(tags.getTagsToAdd());
            if (tags.getTagsToRemove() != null)
                OneSignal.deleteTags(tags.getTagsToRemove(), null);
        }
    }

    private void firePublicClickHandler(@NonNull final String messageId, @NonNull final OSInAppMessageAction action) {
        if (OneSignal.inAppMessageClickHandler == null)
            return;

        OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
                // Send public outcome from handler
                // Send public outcome not from handler
                // Check that only on the handler
                // Any outcome sent on this callback should count as DIRECT from this IAM
                OneSignal.getSessionManager().onDirectInfluenceFromIAMClick(messageId);
                OneSignal.inAppMessageClickHandler.inAppMessageClicked(action);
            }
        });
    }

    private void fireClickAction(@NonNull final OSInAppMessageAction action) {
        if (action.getClickUrl() != null && !action.getClickUrl().isEmpty()) {
            if (action.getUrlTarget() == OSInAppMessageAction.OSInAppMessageActionUrlType.BROWSER)
                OSUtils.openURLInBrowser(action.getClickUrl());
            else if (action.getUrlTarget() == OSInAppMessageAction.OSInAppMessageActionUrlType.IN_APP_WEBVIEW)
                OneSignalChromeTab.open(action.getClickUrl(), true);
        }
    }

    private void saveViewedPageIdsToPrefs() {
        OneSignalPrefs.saveStringSet(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_PAGE_IMPRESSIONED_IAMS,
                // Post success, store impressioned pages to disk
                viewedPageIds);
    }

    private void fireRESTCallForPageChange(@NonNull final OSInAppMessage message, @NonNull final OSInAppMessagePage page) {
        final String variantId = variantIdForMessage(message);
        if (variantId == null)
            return;

        final String pageId = page.getPageId();

        final String messagePrefixedPageId = message.messageId + pageId;

        // Never send multiple page impressions for the same message UUID unless that page change is from an IAM with redisplay
        if (viewedPageIds.contains(messagePrefixedPageId)) {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Already sent page impression for id: " + pageId);
            return;
        }

        viewedPageIds.add(messagePrefixedPageId);

        try {
            JSONObject json = new JSONObject() {{
                put("app_id", OneSignal.appId);
                put("player_id", OneSignal.getUserId());
                put("variant_id", variantId);
                put("device_type", new OSUtils().getDeviceType());
                put("page_id", pageId);
            }};

            OneSignalRestClient.post("in_app_messages/" + message.messageId + "/pageImpression", json, new ResponseHandler() {
                @Override
                void onSuccess(String response) {
                    printHttpSuccessForInAppMessageRequest("page impression", response);
                    saveViewedPageIdsToPrefs();
                }

                @Override
                void onFailure(int statusCode, String response, Throwable throwable) {
                    printHttpErrorForInAppMessageRequest("page impression", statusCode, response);
                    // Post failed, viewed page should be removed and this way another post can be attempted
                    viewedPageIds.remove(messagePrefixedPageId);
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, "Unable to execute in-app message impression HTTP request due to invalid JSON");
        }
    }

    private void fireRESTCallForClick(@NonNull final OSInAppMessage message, @NonNull final OSInAppMessageAction action) {
        final String variantId = variantIdForMessage(message);
        if (variantId == null)
            return;

        final String clickId = action.getClickId();
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
                if (action.isFirstClick())
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
                    clickedClickIds.remove(action.getClickId());
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
                // Pages from different IAMs should not impact each other so we can clear the entire
                // list when an IAM is dismissed or we are re-displaying the same one
                viewedPageIds.clear();
                saveViewedPageIdsToPrefs();
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
                logger.debug("In app message with id, " + message.messageId + ", added to the queue");
            }

            attemptToShowInAppMessage();
        }
    }

    private void attemptToShowInAppMessage() {
        synchronized (messageDisplayQueue) {
            // We need to wait for system conditions to be the correct ones
            if (!systemConditionController.systemConditionsAvailable()) {
                logger.warning("In app message not showing due to system condition not correct");
                return;
            }

            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "displayFirstIAMOnQueue: " + messageDisplayQueue);
            // If there are IAMs in the queue and nothing showing, show first in the queue
            if (messageDisplayQueue.size() > 0 && !isInAppMessageShowing()) {
                logger.debug("No IAM showing currently, showing first item in the queue!");
                displayMessage(messageDisplayQueue.get(0));
                return;
            }

            logger.debug("In app message is currently showing or there are no IAMs left in the queue!");
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
            logger.debug("OSInAppMessageController messageWasDismissed dismissedMessages: " + dismissedMessages.toString());
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
            logger.debug("Stop evaluateMessageDisplayQueue because prompt is currently displayed");
            return;
        }

        inAppMessageShowing = false;
        synchronized (messageDisplayQueue) {
            if (messageDisplayQueue.size() > 0) {
                if (message != null && !messageDisplayQueue.contains(message)) {
                    logger.debug("Message already removed from the queue!");
                    return;
                } else {
                    String removedMessageId = messageDisplayQueue.remove(0).messageId;
                    logger.debug("In app message with id, " + removedMessageId + ", dismissed (removed) from the queue!");
                }
            }

            // Display the next message in the queue, or attempt to add more IAMs to the queue
            if (messageDisplayQueue.size() > 0) {
                logger.debug("In app message on queue available: " + messageDisplayQueue.get(0).messageId);
                displayMessage(messageDisplayQueue.get(0));
            } else {
                logger.debug("In app message dismissed evaluating messages");
                evaluateInAppMessages();
            }
        }
    }

    private void persistInAppMessage(final OSInAppMessage message) {
        long displayTimeSeconds = OneSignal.getTime().getCurrentTimeMillis() / 1000;
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

        logger.debug("persistInAppMessageForRedisplay: " + message.toString() + " with msg array data: " + redisplayedInAppMessages.toString());
    }

    private @Nullable String htmlPathForMessage(OSInAppMessage message) {
        String variantId = variantIdForMessage(message);

        if (variantId == null) {
            logger.error("Unable to find a variant for in-app message " + message.messageId);
            return null;
        }

        return "in_app_messages/" + message.messageId + "/variants/" + variantId + "/html?app_id=" + OneSignal.appId;
    }

    private void displayMessage(@NonNull final OSInAppMessage message) {
        if (!inAppMessagingEnabled) {
            logger.verbose("In app messaging is currently paused, in app messages will not be shown!");
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
                    WebViewManager.showHTMLString(message, taggedHTMLString(htmlStr));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, null);
    }

    String getTagsString() {
        return "{player_name : \"placeholder\"}";
    }

    String taggedHTMLString(@NonNull String untaggedString) {
        String tagsDict = getTagsString();
        String tagScript =  "\n\n" +
                            "<script>\n" +
                            "    iamInfo.tags = %s;\n" +
                            "</script>";
        return untaggedString + String.format(tagScript, tagsDict);
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
                logger.debug("Trigger changed for message: " + message.toString());
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
    void addTriggers(@NonNull Map<String, Object> newTriggers) {
        logger.debug("Triggers added: " + newTriggers.toString());
        triggerController.addTriggers(newTriggers);
        makeRedisplayMessagesAvailableWithTriggers(newTriggers.keySet());
        evaluateInAppMessages();
    }

    void removeTriggersForKeys(Collection<String> keys) {
        logger.debug("Triggers key to remove: " + keys.toString());
        triggerController.removeTriggersForKeys(keys);
        makeRedisplayMessagesAvailableWithTriggers(keys);
        evaluateInAppMessages();
    }

    Map<String, Object> getTriggers() {
        return new HashMap<>(triggerController.getTriggers());
    }

    boolean inAppMessagingEnabled() {
        return inAppMessagingEnabled;
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
