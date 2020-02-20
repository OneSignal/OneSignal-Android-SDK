package com.onesignal;

import android.os.Build;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

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
    private static ArrayList<String> PREFERRED_VARIANT_ORDER = new ArrayList<String>() {{
        add("android");
        add("app");
        add("all");
    }};

    public static final String IN_APP_MESSAGES_JSON_KEY = "in_app_messages";
    private static final String OS_SAVE_IN_APP_MESSAGE = "OS_SAVE_IN_APP_MESSAGE";
    private static final String OS_DELETE_IN_APP_MESSAGE = "OS_DELETE_IN_APP_MESSAGE";

    OSTriggerController triggerController;
    private OSSystemConditionController systemConditionController;
    private OSInAppMessageRepository inAppMessageRepository;

    // IAMs loaded remotely from on_session
    //   If on_session won't be called this will be loaded from cache
    @NonNull private ArrayList<OSInAppMessage> messages;
    // IAMs that have had their trigger(s) evaluated to true;
    //   This mean they have been added to the queue to display, or have already displayed
    @NonNull final private Set<String> dismissedMessages;
    // IAMs that have been displayed to the user
    //   This means their impression has been successfully posted to our backend and should not be counted again
    @NonNull final private Set<String> impressionedMessages;
    // IAM clicks that have been successfully posted to our backend and should not be counted again
    @NonNull final private Set<String> clickedClickIds;
    // Ordered IAMs queued to display, includes the message currently displaying, if any.
    @NonNull final ArrayList<OSInAppMessage> messageDisplayQueue;
    // IAMs displayed with last displayed time and quantity of displays data
    // This is retrieved from a DB Table that take care of each object to be unique
    @NonNull private List<OSInAppMessage> redisplayedInAppMessages;

    private boolean inAppMessagingEnabled = true;

    @Nullable Date lastTimeInAppDismissed;

    @Nullable private static OSInAppMessageController sharedInstance;
    public static OSInAppMessageController getController() {
        OneSignalDbHelper dbHelper = OneSignal.getDBHelperInstance();

        // Make sure only Android 4.4 devices and higher can use IAMs
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            sharedInstance = new OSInAppMessageDummyController(null);
        }

        if (sharedInstance == null)
            sharedInstance = new OSInAppMessageController(dbHelper);

        return sharedInstance;
    }

    protected OSInAppMessageController(OneSignalDbHelper dbInstance) {
        messages = new ArrayList<>();
        dismissedMessages = OSUtils.newConcurrentSet();
        impressionedMessages = OSUtils.newConcurrentSet();
        clickedClickIds = OSUtils.newConcurrentSet();
        messageDisplayQueue = new ArrayList<>();
        triggerController = new OSTriggerController(this);
        systemConditionController = new OSSystemConditionController(this);

        Set<String> tempDismissedSet = OneSignalPrefs.getStringSet(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_DISPLAYED_IAMS,
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

        initRedisplayData(dbInstance);
    }

    void initRedisplayData(OneSignalDbHelper dbInstance) {
        inAppMessageRepository = new OSInAppMessageRepository(dbInstance);
        redisplayedInAppMessages = inAppMessageRepository.getRedisplayedInAppMessages();

        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "redisplayedInAppMessages: " + redisplayedInAppMessages.toString());
    }

    // Normally we wait until on_session call to download the latest IAMs
    //    however an on session won't happen
    void initWithCachedInAppMessages() {
        // Do not reload from cache if already loaded.
        if (!messages.isEmpty())
            return;

        String cachedIamsStr = OneSignalPrefs.getString(
           OneSignalPrefs.PREFS_ONESIGNAL,
           OneSignalPrefs.PREFS_OS_CACHED_IAMS,
           null
        );
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "initWithCachedInAppMessages: " + cachedIamsStr);

        if (cachedIamsStr == null)
            return;

        try {
            processInAppMessageJson(new JSONArray(cachedIamsStr));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // Called after the device is registered from UserStateSynchronizer
    //    which is the REST call to create the player record on_session
    void receivedInAppMessageJson(@NonNull JSONArray json) throws JSONException {
        // Cache copy for quick cold starts
        OneSignalPrefs.saveString(OneSignalPrefs.PREFS_ONESIGNAL,
           OneSignalPrefs.PREFS_OS_CACHED_IAMS, json.toString());

        resetRedisplayMessagesBySession();
        processInAppMessageJson(json);
        deleteOldRedisplayedInAppMessages();
    }

    private void resetRedisplayMessagesBySession() {
        for (OSInAppMessage redisplayInAppMessage : redisplayedInAppMessages) {
            redisplayInAppMessage.setDisplayedInSession(false);
        }
    }

    private void processInAppMessageJson(@NonNull JSONArray json) throws JSONException {
        ArrayList<OSInAppMessage> newMessages = new ArrayList<>();
        for (int i = 0; i < json.length(); i++) {
            JSONObject messageJson = json.getJSONObject(i);
            OSInAppMessage message = new OSInAppMessage(messageJson);
            newMessages.add(message);
        }
        messages = newMessages;

        evaluateInAppMessages();
    }

    private void deleteOldRedisplayedInAppMessages() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);
                inAppMessageRepository.deleteOldRedisplayedInAppMessages();
            }
        }, OS_DELETE_IN_APP_MESSAGE).start();
    }

    private void evaluateInAppMessages() {
        if (systemConditionController.systemConditionsAvailable()) {
            for (OSInAppMessage message : messages) {
                setDataForRedisplay(message);
                if (triggerController.evaluateMessageTriggers(message))
                    messageCanBeDisplayed(message);
            }
        }
    }

    private static @Nullable String variantIdForMessage(@NonNull OSInAppMessage message) {
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

    void onMessageActionOccurredOnMessage(@NonNull final OSInAppMessage message, @NonNull final JSONObject actionJson) {
        final OSInAppMessageAction action = new OSInAppMessageAction(actionJson);
        action.firstClick = message.takeActionAsUnique();

        firePublicClickHandler(action);
        fireClickAction(action);
        fireRESTCallForClick(message, action);
    }

    void onMessageActionOccurredOnPreview(@NonNull final OSInAppMessage message, @NonNull final JSONObject actionJson) {
        final OSInAppMessageAction action = new OSInAppMessageAction(actionJson);
        action.firstClick = message.takeActionAsUnique();

        firePublicClickHandler(action);
        fireClickAction(action);
    }

    private void firePublicClickHandler(@NonNull final OSInAppMessageAction action) {
        if (OneSignal.mInitBuilder.mInAppMessageClickHandler == null)
            return;
        OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
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
        boolean clickAvailableByRedisplay = message.getDisplayStats().isRedisplayEnabled() && message.isClickAvailable(clickId);

        // Never count multiple clicks for the same click UUID unless that click is from an IAM with redisplay
        if (!clickAvailableByRedisplay && clickedClickIds.contains(clickId))
            return;
        clickedClickIds.add(clickId);
        // Track clickId per IAM
        message.addClickId(clickId);

        try {
            JSONObject json = new JSONObject() {{
                put("app_id", OneSignal.appId);
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
     *
     * In order to redisplay an IAM, the following conditions must be satisfied:
     *     1. IAM has redisplay property
     *     2. Time delay between redisplay satisfied
     *     3. Has more redisplays
     *     4. An IAM trigger was satisfied
     *
     * For redisplay, the message need to be removed from the arrays that track the display/impression
     * For click counting, every message has it click id array
     * */
    private void setDataForRedisplay(OSInAppMessage message) {
        if (!message.getDisplayStats().isRedisplayEnabled())
            return;

        boolean messageDismissed = dismissedMessages.contains(message.messageId);
        int index = redisplayedInAppMessages.indexOf(message);

        if (messageDismissed && index != -1) {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "setDataForRedisplay: " + message.messageId);

            OSInAppMessage savedIAM = redisplayedInAppMessages.get(index);
            message.getDisplayStats().setDisplayStats(savedIAM.getDisplayStats());

            // Message that don't have triggers should display only once per session
            boolean triggerHasChanged = message.isTriggerChanged() || (!savedIAM.isDisplayedInSession() && message.triggers.isEmpty());
            // Check if conditions are correct for redisplay
            if (triggerHasChanged &&
                    message.getDisplayStats().isDelayTimeSatisfied() &&
                    message.getDisplayStats().shouldDisplayAgain()) {
                dismissedMessages.remove(message.messageId);
                impressionedMessages.remove(message.messageId);
                message.clearClickIds();
            }
        }
    }

    private void messageCanBeDisplayed(@NonNull OSInAppMessage message) {
        if (!inAppMessagingEnabled)
            return;

       if (dismissedMessages.contains(message.messageId) &&
                !message.isPreview) {
            OneSignal.Log(
                    OneSignal.LOG_LEVEL.ERROR,
                    "In-App message with id '" +
                            message.messageId +
                            "' already displayed or is already preparing to be display!");
            return;
        }

        queueMessageForDisplay(message);
    }

    // Message has passed triggers and de-duplication logic.
    // Display message now or add it to the queue to be displayed.
    private void queueMessageForDisplay(@NonNull OSInAppMessage message) {
        synchronized (messageDisplayQueue) {
            messageDisplayQueue.add(message);
            if (!message.isPreview)
                dismissedMessages.add(message.messageId);

            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "queueMessageForDisplay: " + messageDisplayQueue);

            if (messageDisplayQueue.size() > 1) {
                // means we are already displaying a message
                // this message will be displayed afterwards
                return;
            }

            displayMessage(message);
        }
    }

    boolean isDisplayingInApp() {
        return messageDisplayQueue.size() > 0;
    }

    @Nullable
    OSInAppMessage getCurrentDisplayedInAppMessage() {
        return isDisplayingInApp() ? messageDisplayQueue.get(0) : null;
    }

    // Called after an In-App message is closed and it's dismiss animation has completed
    public void messageWasDismissed(@NonNull OSInAppMessage message) {
        synchronized (messageDisplayQueue) {
            if (!messageDisplayQueue.remove(message)) {
                if (!message.isPreview)
                    OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "An in-app message was removed from the display queue before it was finished displaying.");
                return;
            }

            if (!message.isPreview) {
                persistDisplayedIAMs();
                persistInAppMessageForRedisplay(message);
            }

            // Display the next message in the queue, if any
            if (messageDisplayQueue.size() > 0)
                displayMessage(messageDisplayQueue.get(0));
            else {
                lastTimeInAppDismissed = new Date();
                evaluateInAppMessages();
            }
        }
    }

    private void persistDisplayedIAMs() {
        OneSignalPrefs.saveStringSet(
           OneSignalPrefs.PREFS_ONESIGNAL,
           OneSignalPrefs.PREFS_OS_DISPLAYED_IAMS,
           // Persisting only ones dismissed / opened in case the user didn't have a chance
           //  to interact with the message.
           getAllDismissedIAMs()
        );
    }

    private void persistInAppMessageForRedisplay(final OSInAppMessage message) {
        //If the IAM doesn't have the re display configuration then no need to save it
        if (!message.getDisplayStats().isRedisplayEnabled())
            return;

        long displayTimeSeconds = System.currentTimeMillis() / 1000;
        message.getDisplayStats().setLastDisplayTime(displayTimeSeconds);
        message.getDisplayStats().incrementDisplayQuantity();
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

    // Calculate all dismissed as dismissedMessages minus any in the display queue
    private @NonNull Set<String> getAllDismissedIAMs() {
        Set<String> dismissedIAMs = new HashSet<>(dismissedMessages);
        synchronized (messageDisplayQueue) {
            for (OSInAppMessage message : messageDisplayQueue)
                dismissedIAMs.remove(message.messageId);
        }
        return dismissedIAMs;
    }

    private static @Nullable
    String htmlPathForMessage(OSInAppMessage message) {
        String variantId = variantIdForMessage(message);

        if (variantId == null) {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, "Unable to find a variant for in-app message " + message.messageId);
            return null;
        }

        return "in_app_messages/" + message.messageId + "/variants/" + variantId + "/html?app_id=" + OneSignal.appId;
    }

    public void displayMessage(@NonNull final OSInAppMessage message) {
        String htmlPath = htmlPathForMessage(message);
        OneSignalRestClient.getSync(htmlPath, new ResponseHandler() {
            @Override
            void onFailure(int statusCode, String response, Throwable throwable) {
                printHttpErrorForInAppMessageRequest("html", statusCode, response);
            }

            @Override
            void onSuccess(String response) {
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    String htmlStr = jsonResponse.getString("html");

                    double displayDuration = jsonResponse.optDouble("display_duration");
                    message.setDisplayDuration(displayDuration);

                    WebViewManager.showHTMLString(message, htmlStr);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, null);
    }

    void displayPreviewMessage(@NonNull String previewUUID) {
        String htmlPath = "in_app_messages/device_preview?preview_id=" + previewUUID + "&app_id=" + OneSignal.appId;
        OneSignalRestClient.get(htmlPath, new ResponseHandler() {
            @Override
            void onFailure(int statusCode, String response, Throwable throwable) {
                printHttpErrorForInAppMessageRequest("html", statusCode, response);
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

    @Override
    public void messageTriggerConditionChanged() {
        // This method is called when a time-based trigger timer fires, meaning the message can
        // probably be shown now. So the current message conditions should be re-evaluated
        evaluateInAppMessages();
    }

    /**
     * Part of redisplay logic
     *
     * Make all messages with redisplay available if:
     *   - Already displayed
     *   - At least one Trigger has changed
     */
    private void makeRedisplayMessagesAvailableWithTriggers(Collection<String> newTriggersKeys) {
        for (OSInAppMessage message : messages) {
            if (redisplayedInAppMessages.contains(message) &&
                    triggerController.isTriggerOnMessage(message, newTriggersKeys)) {
                message.setTriggerChanged(true);
            }
        }
    }

    /**
     * Trigger logic
     * <p>
     * These methods mostly pass data to the Trigger Controller, but also cause the SDK to
     * re-evaluate messages to see if we should display a message now that the trigger
     * conditions have changed.
     */
    void addTriggers(Map<String, Object> newTriggers) {
        triggerController.addTriggers(newTriggers);
        makeRedisplayMessagesAvailableWithTriggers(newTriggers.keySet());
        evaluateInAppMessages();
    }

    void removeTriggersForKeys(Collection<String> keys) {
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
}
