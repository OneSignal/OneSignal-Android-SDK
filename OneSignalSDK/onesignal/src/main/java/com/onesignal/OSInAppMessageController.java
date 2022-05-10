package com.onesignal;

import android.app.AlertDialog;
import android.content.DialogInterface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.onesignal.OSDynamicTriggerController.OSDynamicTriggerControllerObserver;
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

import static com.onesignal.OSInAppMessageRepository.IAM_DATA_RESPONSE_RETRY_KEY;

class OSInAppMessageController extends OSBackgroundManager implements OSDynamicTriggerControllerObserver, OSSystemConditionController.OSSystemConditionObserver {

    private static final Object LOCK = new Object();
    private final static String OS_IAM_DB_ACCESS = "OS_IAM_DB_ACCESS";
    public static final String IN_APP_MESSAGES_JSON_KEY = "in_app_messages";
    private static final String LIQUID_TAG_SCRIPT = "\n\n" +
            "<script>\n" +
            "    setPlayerTags(%s);\n" +
            "</script>";
    private static ArrayList<String> PREFERRED_VARIANT_ORDER = new ArrayList<String>() {{
        add("android");
        add("app");
        add("all");
    }};

    private final OSLogger logger;
    private final OSTaskController taskController;
    private final LanguageContext languageContext;

    private OSSystemConditionController systemConditionController;
    private OSInAppMessageRepository inAppMessageRepository;
    private OSInAppMessageLifecycleHandler inAppMessageLifecycleHandler;

    OSTriggerController triggerController;

    // IAMs loaded remotely from on_session
    //   If on_session won't be called this will be loaded from cache
    @NonNull
    private ArrayList<OSInAppMessageInternal> messages;
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
    final private ArrayList<OSInAppMessageInternal> messageDisplayQueue;
    // IAMs displayed with last displayed time and quantity of displays data
    // This is retrieved from a DB Table that take care of each object to be unique
    @Nullable
    private List<OSInAppMessageInternal> redisplayedInAppMessages = null;

    private OSInAppMessagePrompt currentPrompt = null;
    private boolean inAppMessagingEnabled = true;
    private boolean inAppMessageShowing = false;

    @Nullable
    private String userTagsString = null;

    @Nullable
    private OSInAppMessageContent pendingMessageContent = null;

    private boolean waitForTags = false;

    @Nullable
    Date lastTimeInAppDismissed = null;

    protected OSInAppMessageController(OneSignalDbHelper dbHelper, OSTaskController controller, OSLogger logger,
                                       OSSharedPreferences sharedPreferences, LanguageContext languageContext) {
        taskController = controller;
        messages = new ArrayList<>();
        dismissedMessages = OSUtils.newConcurrentSet();
        messageDisplayQueue = new ArrayList<>();
        impressionedMessages = OSUtils.newConcurrentSet();
        viewedPageIds = OSUtils.newConcurrentSet();
        clickedClickIds = OSUtils.newConcurrentSet();
        triggerController = new OSTriggerController(this);
        systemConditionController = new OSSystemConditionController(this);
        this.languageContext = languageContext;
        this.logger = logger;

        inAppMessageRepository = getInAppMessageRepository(dbHelper, logger, sharedPreferences);
        Set<String> tempDismissedSet = inAppMessageRepository.getDismissedMessagesId();
        if (tempDismissedSet != null)
            dismissedMessages.addAll(tempDismissedSet);

        Set<String> tempImpressionsSet = inAppMessageRepository.getImpressionesMessagesId();
        if (tempImpressionsSet != null)
            impressionedMessages.addAll(tempImpressionsSet);

        Set<String> tempPageImpressionsSet = inAppMessageRepository.getViewPageImpressionedIds();
        if (tempPageImpressionsSet != null)
            viewedPageIds.addAll(tempPageImpressionsSet);

        Set<String> tempClickedMessageIdsSet = inAppMessageRepository.getClickedMessagesId();
        if (tempClickedMessageIdsSet != null)
            clickedClickIds.addAll(tempClickedMessageIdsSet);

        Date tempLastTimeInAppDismissed = inAppMessageRepository.getLastTimeInAppDismissed();
        if (tempLastTimeInAppDismissed != null) {
            lastTimeInAppDismissed = tempLastTimeInAppDismissed;
        }

        initRedisplayData();
    }

    OSInAppMessageRepository getInAppMessageRepository(OneSignalDbHelper dbHelper, OSLogger logger, OSSharedPreferences sharedPreferences) {
        if (inAppMessageRepository == null)
            inAppMessageRepository = new OSInAppMessageRepository(dbHelper, logger, sharedPreferences);

        return inAppMessageRepository;
    }

    protected void initRedisplayData() {
        Runnable getCachedIAMRunnable = new BackgroundRunnable() {
            @Override
            public void run() {
                super.run();

                synchronized (LOCK) {
                    redisplayedInAppMessages = inAppMessageRepository.getCachedInAppMessages();
                    logger.debug("Retrieved IAMs from DB redisplayedInAppMessages: " + redisplayedInAppMessages.toString());
                }
            }
        };

        taskController.addTaskToQueue(getCachedIAMRunnable);
        taskController.startPendingTasks();
    }

    boolean shouldRunTaskThroughQueue() {
        synchronized (LOCK) {
            return redisplayedInAppMessages == null && taskController.shouldRunTaskThroughQueue();
        }
    }

    void executeRedisplayIAMDataDependantTask(Runnable task) {
        synchronized (LOCK) {
            if (shouldRunTaskThroughQueue()) {
                logger.debug("Delaying task due to redisplay data not retrieved yet");
                taskController.addTaskToQueue(task);
            } else {
                task.run();
            }
        }
    }

    void resetSessionLaunchTime() {
        OSDynamicTriggerController.resetSessionLaunchTime();
    }

    // Normally we wait until on_session call to download the latest IAMs
    //    however an on session won't happen
    void initWithCachedInAppMessages() {
        // Do not reload from cache if already loaded.
        if (!messages.isEmpty()) {
            logger.debug("initWithCachedInAppMessages with already in memory messages: " + messages);
            return;
        }

        String cachedInAppMessageString = inAppMessageRepository.getSavedIAMs();
        logger.debug("initWithCachedInAppMessages: " + cachedInAppMessageString);

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
    void receivedInAppMessageJson(@NonNull final JSONArray json) throws JSONException {
        // Cache copy for quick cold starts
        inAppMessageRepository.saveIAMs(json.toString());

        executeRedisplayIAMDataDependantTask(new Runnable() {
            @Override
            public void run() {
                resetRedisplayMessagesBySession();
                try {
                    processInAppMessageJson(json);
                } catch (JSONException e) {
                    logger.error("ERROR processing InAppMessageJson JSON Response.", e);
                }
            }
        });
    }

    private void resetRedisplayMessagesBySession() {
        for (OSInAppMessageInternal redisplayInAppMessage : redisplayedInAppMessages) {
            redisplayInAppMessage.setDisplayedInSession(false);
        }
    }

    private void processInAppMessageJson(@NonNull JSONArray json) throws JSONException {
        synchronized (LOCK) {
            ArrayList<OSInAppMessageInternal> newMessages = new ArrayList<>();
            for (int i = 0; i < json.length(); i++) {
                JSONObject messageJson = json.getJSONObject(i);
                OSInAppMessageInternal message = new OSInAppMessageInternal(messageJson);
                // Avoid null checks later if IAM already comes with null id
                if (message.messageId != null) {
                    newMessages.add(message);
                }
            }

            messages = newMessages;
        }

        evaluateInAppMessages();
    }

    private void evaluateInAppMessages() {
        logger.debug("Starting evaluateInAppMessages");

        if (shouldRunTaskThroughQueue()) {
            taskController.addTaskToQueue(new Runnable() {
                @Override
                public void run() {
                    logger.debug("Delaying evaluateInAppMessages due to redisplay data not retrieved yet");
                    evaluateInAppMessages();
                }
            });
            return;
        }

        for (OSInAppMessageInternal message : messages) {
            // Make trigger evaluation first, dynamic trigger might change "trigger changed" flag value for redisplay messages
            if (triggerController.evaluateMessageTriggers(message)) {
                setDataForRedisplay(message);

                if (!dismissedMessages.contains(message.messageId) && !message.isFinished()) {
                    queueMessageForDisplay(message);
                }
            }
        }
    }

    private @Nullable String variantIdForMessage(@NonNull OSInAppMessageInternal message) {
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

    void onMessageWasShown(@NonNull final OSInAppMessageInternal message) {

        onMessageDidDisplay(message);

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

        inAppMessageRepository.sendIAMImpression(OneSignal.appId, OneSignal.getUserId(), variantId,
                new OSUtils().getDeviceType(), message.messageId, impressionedMessages,
                new OSInAppMessageRepository.OSInAppMessageRequestResponse() {
                    @Override
                    public void onSuccess(String response) {
                        // Everything handled by repository
                    }

                    @Override
                    public void onFailure(String response) {
                        // Post failed, impressioned Messages should be removed and this way another post can be attempted
                        impressionedMessages.remove(message.messageId);
                    }
                });
    }

    void onPageChanged(@NonNull final OSInAppMessageInternal message, @NonNull final JSONObject eventJson) {
        final OSInAppMessagePage newPage = new OSInAppMessagePage(eventJson);
        if (message.isPreview) {
            return;
        }
        fireRESTCallForPageChange(message, newPage);
    }

    void onMessageActionOccurredOnMessage(@NonNull final OSInAppMessageInternal message, @NonNull final JSONObject actionJson) throws JSONException {
        final OSInAppMessageAction action = new OSInAppMessageAction(actionJson);
        action.setFirstClick(message.takeActionAsUnique());

        firePublicClickHandler(message.messageId, action);
        beginProcessingPrompts(message, action.getPrompts());
        fireClickAction(action);
        fireRESTCallForClick(message, action);
        fireTagCallForClick(action);
        fireOutcomesForClick(message.messageId, action.getOutcomes());
    }

    void onMessageActionOccurredOnPreview(@NonNull final OSInAppMessageInternal message, @NonNull final JSONObject actionJson) throws JSONException {
        final OSInAppMessageAction action = new OSInAppMessageAction(actionJson);
        action.setFirstClick(message.takeActionAsUnique());

        firePublicClickHandler(message.messageId, action);
        beginProcessingPrompts(message, action.getPrompts());
        fireClickAction(action);
        logInAppMessagePreviewActions(action);
    }

    /**
     * IAM Lifecycle methods
     * The following methods call the public OSInAppMessageLifecycleHandler callbacks
     */
    void setInAppMessageLifecycleHandler(@Nullable OSInAppMessageLifecycleHandler handler) {
        inAppMessageLifecycleHandler = handler;
    }

    void onMessageWillDisplay(@NonNull final OSInAppMessageInternal message) {
        if (inAppMessageLifecycleHandler == null) {
            logger.verbose("OSInAppMessageController onMessageWillDisplay: inAppMessageLifecycleHandler is null");
            return;
        }
        inAppMessageLifecycleHandler.onWillDisplayInAppMessage(message);
    }

    void onMessageDidDisplay(@NonNull final OSInAppMessageInternal message) {
        if (inAppMessageLifecycleHandler == null) {
            logger.verbose("OSInAppMessageController onMessageDidDisplay: inAppMessageLifecycleHandler is null");
            return;
        }
        inAppMessageLifecycleHandler.onDidDisplayInAppMessage(message);
    }

    void onMessageWillDismiss(@NonNull final OSInAppMessageInternal message) {
        if (inAppMessageLifecycleHandler == null) {
            logger.verbose("OSInAppMessageController onMessageWillDismiss: inAppMessageLifecycleHandler is null");
            return;
        }
        inAppMessageLifecycleHandler.onWillDismissInAppMessage(message);
    }

    void onMessageDidDismiss(@NonNull final OSInAppMessageInternal message) {
        if (inAppMessageLifecycleHandler == null) {
            logger.verbose("OSInAppMessageController onMessageDidDismiss: inAppMessageLifecycleHandler is null");
            return;
        }
        inAppMessageLifecycleHandler.onDidDismissInAppMessage(message);
    }

    /* End IAM Lifecycle methods */

    private void logInAppMessagePreviewActions(final OSInAppMessageAction action) {
        if (action.getTags() != null)
            logger.debug("Tags detected inside of the action click payload, ignoring because action came from IAM preview:: " + action.getTags().toString());

        if (action.getOutcomes().size() > 0)
            logger.debug("Outcomes detected inside of the action click payload, ignoring because action came from IAM preview: " + action.getOutcomes().toString());

        // TODO: Add more action payload preview logs here in future
    }

    private void beginProcessingPrompts(OSInAppMessageInternal message, final List<OSInAppMessagePrompt> prompts) {
        if (prompts.size() > 0) {
            logger.debug("IAM showing prompts from IAM: " + message.toString());
            // TODO until we don't fix the activity going forward or back dismissing the IAM, we need to auto dismiss
            WebViewManager.dismissCurrentInAppMessage();
            showMultiplePrompts(message, prompts);
        }
    }

    private void showMultiplePrompts(final OSInAppMessageInternal inAppMessage, final List<OSInAppMessagePrompt> prompts) {
        for (OSInAppMessagePrompt prompt : prompts) {
            // Don't show prompt twice
            if (!prompt.hasPrompted()) {
                currentPrompt = prompt;
                break;
            }
        }

        if (currentPrompt != null) {
            logger.debug("IAM prompt to handle: " + currentPrompt.toString());
            currentPrompt.setPrompted(true);
            currentPrompt.handlePrompt(new OneSignal.OSPromptActionCompletionCallback() {
                @Override
                public void onCompleted(OneSignal.PromptActionResult result) {
                    currentPrompt = null;
                    logger.debug("IAM prompt to handle finished with result: " + result);

                    // On preview mode we show informative alert dialogs
                    if (inAppMessage.isPreview && result == OneSignal.PromptActionResult.LOCATION_PERMISSIONS_MISSING_MANIFEST)
                        showAlertDialogMessage(inAppMessage, prompts);
                    else
                        showMultiplePrompts(inAppMessage, prompts);
                }
            });
        } else {
            logger.debug("No IAM prompt to handle, dismiss message: " + inAppMessage.messageId);
            messageWasDismissed(inAppMessage);
        }
    }

    private void showAlertDialogMessage(final OSInAppMessageInternal inAppMessage, final List<OSInAppMessagePrompt> prompts) {
        final String messageTitle = OneSignal.appContext.getString(R.string.location_permission_missing_title);
        final String message = OneSignal.appContext.getString(R.string.location_permission_missing_message);
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

    private void fireRESTCallForPageChange(@NonNull final OSInAppMessageInternal message, @NonNull final OSInAppMessagePage page) {
        final String variantId = variantIdForMessage(message);
        if (variantId == null)
            return;

        final String pageId = page.getPageId();

        final String messagePrefixedPageId = message.messageId + pageId;

        // Never send multiple page impressions for the same message UUID unless that page change is from an IAM with redisplay
        if (viewedPageIds.contains(messagePrefixedPageId)) {
            logger.verbose("Already sent page impression for id: " + pageId);
            return;
        }

        viewedPageIds.add(messagePrefixedPageId);

        inAppMessageRepository.sendIAMPageImpression(OneSignal.appId, OneSignal.getUserId(), variantId, new OSUtils().getDeviceType(),
                message.messageId, pageId, viewedPageIds, new OSInAppMessageRepository.OSInAppMessageRequestResponse() {
                    @Override
                    public void onSuccess(String response) {
                        // Everything handled by repository
                    }

                    @Override
                    public void onFailure(String response) {
                        // Post failed, viewed page should be removed and this way another post can be attempted
                        viewedPageIds.remove(messagePrefixedPageId);
                    }
                });
    }

    private void fireRESTCallForClick(@NonNull final OSInAppMessageInternal message, @NonNull final OSInAppMessageAction action) {
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

        inAppMessageRepository.sendIAMClick(OneSignal.appId, OneSignal.getUserId(), variantId, new OSUtils().getDeviceType(),
                message.messageId, clickId, action.isFirstClick(), clickedClickIds, new OSInAppMessageRepository.OSInAppMessageRequestResponse() {
                    @Override
                    public void onSuccess(String response) {
                        // Everything handled by repository
                    }

                    @Override
                    public void onFailure(String response) {
                        clickedClickIds.remove(clickId);
                        message.removeClickId(clickId);
                    }
                });
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
    private void setDataForRedisplay(OSInAppMessageInternal message) {
        boolean messageDismissed = dismissedMessages.contains(message.messageId);
        int index = redisplayedInAppMessages.indexOf(message);

        if (messageDismissed && index != -1) {
            OSInAppMessageInternal savedIAM = redisplayedInAppMessages.get(index);
            message.getRedisplayStats().setDisplayStats(savedIAM.getRedisplayStats());
            message.setDisplayedInSession(savedIAM.isDisplayedInSession());

            boolean triggerHasChanged = hasMessageTriggerChanged(message);
            logger.debug("setDataForRedisplay: " + message.toString() + " triggerHasChanged: " + triggerHasChanged);

            // Check if conditions are correct for redisplay
            if (triggerHasChanged &&
                    message.getRedisplayStats().isDelayTimeSatisfied() &&
                    message.getRedisplayStats().shouldDisplayAgain()) {
                logger.debug("setDataForRedisplay message available for redisplay: " + message.messageId);

                dismissedMessages.remove(message.messageId);
                impressionedMessages.remove(message.messageId);
                // Pages from different IAMs should not impact each other so we can clear the entire
                // list when an IAM is dismissed or we are re-displaying the same one
                viewedPageIds.clear();
                inAppMessageRepository.saveViewPageImpressionedIds(viewedPageIds);
                message.clearClickIds();
            }
        }
    }

    private boolean hasMessageTriggerChanged(OSInAppMessageInternal message) {
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
    private void queueMessageForDisplay(@NonNull OSInAppMessageInternal message) {
        synchronized (messageDisplayQueue) {
            // Make sure no message is ever added to the queue more than once
            if (!messageDisplayQueue.contains(message)) {
                messageDisplayQueue.add(message);
                logger.debug("In app message with id: " + message.messageId + ", added to the queue");
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

            logger.debug("displayFirstIAMOnQueue: " + messageDisplayQueue);
            // If there are IAMs in the queue and nothing showing, show first in the queue
            if (messageDisplayQueue.size() > 0 && !isInAppMessageShowing()) {
                logger.debug("No IAM showing currently, showing first item in the queue!");
                displayMessage(messageDisplayQueue.get(0));
                return;
            }

            logger.debug("In app message is currently showing or there are no IAMs left in the queue! isInAppMessageShowing: " + isInAppMessageShowing());
        }
    }

    boolean isInAppMessageShowing() {
        return inAppMessageShowing;
    }

    @Nullable
    OSInAppMessageInternal getCurrentDisplayedInAppMessage() {
        // When in app messaging is paused, the messageDisplayQueue might have IAMs, so return null
        return inAppMessageShowing ? messageDisplayQueue.get(0) : null;
    }

    /**
     * Called after an In-App message is closed and it's dismiss animation has completed
     */
    void messageWasDismissed(@NonNull OSInAppMessageInternal message) {
        messageWasDismissed(message, false);
    }

    void messageWasDismissed(@NonNull OSInAppMessageInternal message, boolean failed) {

        if (!message.isPreview) {
            dismissedMessages.add(message.messageId);
            // If failed we will retry on next session
            if (!failed) {
                inAppMessageRepository.saveDismissedMessagesId(dismissedMessages);

                // Don't keep track of last displayed time for a preview
                lastTimeInAppDismissed = new Date();
                // Only increase IAM display quantity if IAM was truly displayed
                persistInAppMessage(message);
            }
            logger.debug("OSInAppMessageController messageWasDismissed dismissedMessages: " + dismissedMessages.toString());
        }

        if (!shouldWaitForPromptsBeforeDismiss())
            onMessageDidDismiss(message);

        dismissCurrentMessage(message);
    }

    private boolean shouldWaitForPromptsBeforeDismiss() {
        return currentPrompt != null;
    }

    /**
     * Removes first item from the queue and attempts to show the next IAM in the queue
     *
     * @param message The message dismissed, preview messages are null
     */
    private void dismissCurrentMessage(@Nullable OSInAppMessageInternal message) {
        // Remove DIRECT influence due to ClickHandler of ClickAction outcomes
        OneSignal.getSessionManager().onDirectInfluenceFromIAMClickFinished();

        if (shouldWaitForPromptsBeforeDismiss()) {
            logger.debug("Stop evaluateMessageDisplayQueue because prompt is currently displayed");
            return;
        }

        inAppMessageShowing = false;
        synchronized (messageDisplayQueue) {
            if (message != null && !message.isPreview && messageDisplayQueue.size() > 0) {
                if (!messageDisplayQueue.contains(message)) {
                    logger.debug("Message already removed from the queue!");
                    return;
                } else {
                    String removedMessageId = messageDisplayQueue.remove(0).messageId;
                    logger.debug("In app message with id: " + removedMessageId + ", dismissed (removed) from the queue!");
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

    private void persistInAppMessage(final OSInAppMessageInternal message) {
        long displayTimeSeconds = OneSignal.getTime().getCurrentTimeMillis() / 1000;
        message.getRedisplayStats().setLastDisplayTime(displayTimeSeconds);
        message.getRedisplayStats().incrementDisplayQuantity();
        message.setTriggerChanged(false);
        message.setDisplayedInSession(true);

        Runnable saveIAMOnDBRunnable = new BackgroundRunnable() {
            @Override
            public void run() {
                super.run();

                inAppMessageRepository.saveInAppMessage(message);
                inAppMessageRepository.saveLastTimeInAppDismissed(lastTimeInAppDismissed);
            }
        };
        runRunnableOnThread(saveIAMOnDBRunnable, OS_IAM_DB_ACCESS);

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

    private void getTagsForLiquidTemplating(@NonNull final OSInAppMessageInternal message, final boolean isPreview) {
        waitForTags = false;
        if (isPreview || message.getHasLiquid()) {
            waitForTags = true;
            OneSignal.getTags(new OneSignal.OSGetTagsHandler() {
                @Override
                public void tagsAvailable(JSONObject tags) {
                    waitForTags = false;
                    if (tags != null) {
                        userTagsString = tags.toString();
                    }
                    if (pendingMessageContent != null) {
                        if (!isPreview) {
                            OneSignal.getSessionManager().onInAppMessageReceived(message.messageId);
                        }
                        pendingMessageContent.setContentHtml(taggedHTMLString(pendingMessageContent.getContentHtml()));
                        WebViewManager.showMessageContent(message, pendingMessageContent);
                        pendingMessageContent = null;
                    }
                }
            });
        }
    }

    private OSInAppMessageContent parseMessageContentData(JSONObject data, OSInAppMessageInternal message) {
        OSInAppMessageContent content = new OSInAppMessageContent(data);
        message.setDisplayDuration(content.getDisplayDuration());
        return content;
    }

    private void displayMessage(@NonNull final OSInAppMessageInternal message) {
        if (!inAppMessagingEnabled) {
            logger.verbose("In app messaging is currently paused, in app messages will not be shown!");
            return;
        }

        inAppMessageShowing = true;

        getTagsForLiquidTemplating(message, false);

        inAppMessageRepository.getIAMData(OneSignal.appId, message.messageId, variantIdForMessage(message),
                new OSInAppMessageRepository.OSInAppMessageRequestResponse() {
                    @Override
                    public void onSuccess(String response) {
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            OSInAppMessageContent content =  parseMessageContentData(jsonResponse, message);
                            if (content.getContentHtml() == null) {
                                logger.debug("displayMessage:OnSuccess: No HTML retrieved from loadMessageContent");
                                return;
                            }
                            if (waitForTags) {
                                pendingMessageContent = content;
                                return;
                            }
                            OneSignal.getSessionManager().onInAppMessageReceived(message.messageId);
                            onMessageWillDisplay(message);
                            content.setContentHtml(taggedHTMLString(content.getContentHtml()));
                            WebViewManager.showMessageContent(message, content);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onFailure(String response) {
                        inAppMessageShowing = false;
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            boolean retry = jsonResponse.getBoolean(IAM_DATA_RESPONSE_RETRY_KEY);
                            if (retry) {
                                // Retry displaying the same IAM
                                // Using the queueMessageForDisplay method follows safety checks to prevent issues
                                // like having 2 IAMs showing at once or duplicate IAMs in the queue
                                queueMessageForDisplay(message);
                            } else {
                                messageWasDismissed(message, true);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
    }

    @NonNull
    String taggedHTMLString(@NonNull String untaggedString) {
        String tagsDict = userTagsString;
        String tagScript = LIQUID_TAG_SCRIPT;
        return untaggedString + String.format(tagScript, tagsDict);
    }

    void displayPreviewMessage(@NonNull String previewUUID) {
        inAppMessageShowing = true;

        final OSInAppMessageInternal message = new OSInAppMessageInternal(true);
        getTagsForLiquidTemplating(message, true);

        inAppMessageRepository.getIAMPreviewData(OneSignal.appId, previewUUID, new OSInAppMessageRepository.OSInAppMessageRequestResponse() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    OSInAppMessageContent content =  parseMessageContentData(jsonResponse, message);
                    if (content.getContentHtml() == null) {
                        logger.debug("displayPreviewMessage:OnSuccess: No HTML retrieved from loadMessageContent");
                        return;
                    }
                    if (waitForTags) {
                        pendingMessageContent = content;
                        return;
                    }
                    onMessageWillDisplay(message);
                    content.setContentHtml(taggedHTMLString(content.getContentHtml()));
                    WebViewManager.showMessageContent(message, content);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(String response) {
                dismissCurrentMessage(null);
            }
        });
    }

    /**
     * Remove IAMs that the last display time was six month ago
     * 1. Query for all old message ids and old clicked click ids
     * 2. Delete old IAMs from SQL
     * 3. Use queried data to clean SharedPreferences
     */
    void cleanCachedInAppMessages() {
        Runnable cleanCachedIAMRunnable = new BackgroundRunnable() {
            @Override
            public void run() {
                super.run();

                inAppMessageRepository.cleanCachedInAppMessages();
            }
        };

        runRunnableOnThread(cleanCachedIAMRunnable, OS_IAM_DB_ACCESS);
    }

    /**
     * Part of redisplay logic
     * <p>
     * Will update redisplay messages depending on dynamic triggers before setDataForRedisplay is called.
     * @see OSInAppMessageController#setDataForRedisplay(OSInAppMessageInternal)
     *
     * We can't depend only on messageTriggerConditionChanged, due to trigger evaluation to true before scheduling
     * @see OSInAppMessageController#messageTriggerConditionChanged()
     */
    @Override
    public void messageDynamicTriggerCompleted(String triggerId) {
        logger.debug("messageDynamicTriggerCompleted called with triggerId: " + triggerId);
        Set<String> triggerIds = new HashSet<>();
        triggerIds.add(triggerId);
        makeRedisplayMessagesAvailableWithTriggers(triggerIds);
    }

    /**
     * Dynamic trigger logic
     * <p>
     * This will re evaluate messages due to dynamic triggers evaluating to true potentially
     *
     * @see OSInAppMessageController#setDataForRedisplay(OSInAppMessageInternal)
     */
    @Override
    public void messageTriggerConditionChanged() {
        logger.debug("messageTriggerConditionChanged called");

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
        for (OSInAppMessageInternal message : messages) {
            if (!message.isTriggerChanged() && redisplayedInAppMessages.contains(message) &&
                    triggerController.isTriggerOnMessage(message, newTriggersKeys)) {
                logger.debug("Trigger changed for message: " + message.toString());
                message.setTriggerChanged(true);
            }
        }
    }

    private void checkRedisplayMessagesAndEvaluate(Collection<String> newTriggersKeys) {
        makeRedisplayMessagesAvailableWithTriggers(newTriggersKeys);
        evaluateInAppMessages();
    }

    /**
     * Trigger logic
     * <p>
     * These methods mostly pass data to the Trigger Controller, but also cause the SDK to
     * re-evaluate messages to see if we should display/redisplay a message now that the trigger
     * conditions have changed.
     */
    void addTriggers(@NonNull final Map<String, Object> newTriggers) {
        logger.debug("Triggers added: " + newTriggers.toString());
        triggerController.addTriggers(newTriggers);

        if (shouldRunTaskThroughQueue())
            taskController.addTaskToQueue(new Runnable() {
                @Override
                public void run() {
                    logger.debug("Delaying addTriggers due to redisplay data not retrieved yet");
                    checkRedisplayMessagesAndEvaluate(newTriggers.keySet());
                }
            });
        else
            checkRedisplayMessagesAndEvaluate(newTriggers.keySet());
    }

    void removeTriggersForKeys(final Collection<String> keys) {
        logger.debug("Triggers key to remove: " + keys.toString());
        triggerController.removeTriggersForKeys(keys);

        if (shouldRunTaskThroughQueue())
            taskController.addTaskToQueue(new Runnable() {
                @Override
                public void run() {
                    logger.debug("Delaying removeTriggersForKeys due to redisplay data not retrieved yet");
                    checkRedisplayMessagesAndEvaluate(keys);
                }
            });
        else
            checkRedisplayMessagesAndEvaluate(keys);
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
    public ArrayList<OSInAppMessageInternal> getInAppMessageDisplayQueue() {
        return messageDisplayQueue;
    }

    // Method for testing purposes
    @NonNull
    public List<OSInAppMessageInternal> getRedisplayedInAppMessages() {
        return redisplayedInAppMessages;
    }
}
