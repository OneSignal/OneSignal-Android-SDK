package com.onesignal;

import android.os.Build;
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
import java.util.Map;
import java.util.Set;

class OSInAppMessageController implements OSDynamicTriggerControllerObserver, OSSystemConditionController.OSSystemConditionObserver {
    private static ArrayList<String> PREFERRED_VARIANT_ORDER = new ArrayList<String>() {{
        add("android");
        add("app");
        add("all");
    }};

    public static final String IN_APP_MESSAGES_JSON_KEY = "in_app_messages";

    OSTriggerController triggerController;
    private OSSystemConditionController systemConditionController;

    // IAMs loaded remotely from on_session
    //   If on_session won't be called this will be loaded from cache
    @NonNull private ArrayList<OSInAppMessage> messages;
    // IAMs that have had their trigger(s) evaluated to true;
    //   This mean they have been added to the queue to display, or have already displayed
    @NonNull final private Set<String> triggeredMessages;
    // IAMs that have been displayed to the user
    //   This means their impression has been successfully posted to our backend and should not be counted again
    @NonNull final private Set<String> impressionedMessages;
    // IAM clicks that have been successfully posted to our backend and should not be counted again
    @NonNull final private Set<String> clickedClickIds;
    // Ordered IAMs queued to display, includes the message currently displaying, if any.
    @NonNull final ArrayList<OSInAppMessage> messageDisplayQueue;

    private boolean inAppMessagingEnabled = true;

    @Nullable Date lastTimeInAppDismissed;

    @Nullable private static OSInAppMessageController sharedInstance;
    public static OSInAppMessageController getController() {
        // Make sure only Android 4.4 devices and higher can use IAMs
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            sharedInstance = new OSInAppMessageDummyController();
        }

        if (sharedInstance == null)
            sharedInstance = new OSInAppMessageController();

        return sharedInstance;
    }

    protected OSInAppMessageController() {
        messages = new ArrayList<>();
        triggeredMessages = OSUtils.newConcurrentSet();
        impressionedMessages = OSUtils.newConcurrentSet();
        clickedClickIds = OSUtils.newConcurrentSet();
        messageDisplayQueue = new ArrayList<>();
        triggerController = new OSTriggerController(this);
        systemConditionController = new OSSystemConditionController(this);

        Set<String> tempTriggeredSet = OneSignalPrefs.getStringSet(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_DISPLAYED_IAMS,
                null
        );
        if (tempTriggeredSet != null)
            triggeredMessages.addAll(tempTriggeredSet);

        Set<String> tempImpressionedSet = OneSignalPrefs.getStringSet(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_IMPRESSIONED_IAMS,
                null
        );
        if (tempImpressionedSet != null)
            impressionedMessages.addAll(tempImpressionedSet);

        Set<String> tempClickedMessageIdsSet = OneSignalPrefs.getStringSet(
           OneSignalPrefs.PREFS_ONESIGNAL,
           OneSignalPrefs.PREFS_OS_CLICKED_CLICK_IDS_IAMS,
           null
        );
        if (tempClickedMessageIdsSet != null)
            clickedClickIds.addAll(tempClickedMessageIdsSet);
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
        processInAppMessageJson(json);
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

    private void evaluateInAppMessages() {
        if (systemConditionController.systemConditionsAvailable()) {
            for (OSInAppMessage message : messages) {
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

        // Never count multiple clicks for the same click UUID
        if (clickedClickIds.contains(action.clickId))
            return;
        clickedClickIds.add(action.clickId);

        try {
            JSONObject json = new JSONObject() {{
                put("app_id", OneSignal.appId);
                put("device_type", new OSUtils().getDeviceType());
                put("player_id", OneSignal.getUserId());
                put("click_id", action.clickId);
                put("variant_id", variantId);
                if (action.firstClick)
                    put("first_click", true);
            }};

            OneSignalRestClient.post("in_app_messages/" + message.messageId + "/click", json, new ResponseHandler() {
                @Override
                void onSuccess(String response) {
                    printHttpSuccessForInAppMessageRequest("engagement", response);
                    // Persist success click to disk. Id already added to set before making making the network call
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

    private void messageCanBeDisplayed(@NonNull OSInAppMessage message) {
        if (!inAppMessagingEnabled)
            return;

        if (triggeredMessages.contains(message.messageId) &&
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
                triggeredMessages.add(message.messageId);

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
    void messageWasDismissed(@NonNull OSInAppMessage message) {
        synchronized (messageDisplayQueue) {
            if (!messageDisplayQueue.remove(message)) {
                if (!message.isPreview)
                    OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "An in-app message was removed from the display queue before it was finished displaying.");
                return;
            }

            if (!message.isPreview)
                persistDisplayedIams();

            // Display the next message in the queue, if any
            if (messageDisplayQueue.size() > 0)
                displayMessage(messageDisplayQueue.get(0));
            else {
                lastTimeInAppDismissed = new Date();
                evaluateInAppMessages();
            }
        }
    }

    private void persistDisplayedIams() {
        OneSignalPrefs.saveStringSet(
           OneSignalPrefs.PREFS_ONESIGNAL,
           OneSignalPrefs.PREFS_OS_DISPLAYED_IAMS,
           // Persisting only ones dismissed / opened in case the user didn't have a chance
           //  to interact with the message.
           getAllDismissedIams()
        );
    }

    // Calculate all dismissed as triggeredMessages minus any in the display queue
    private @NonNull Set<String> getAllDismissedIams() {
        Set<String> dismissedIams = new HashSet<>(triggeredMessages);
        synchronized (messageDisplayQueue) {
            for(OSInAppMessage message : messageDisplayQueue)
                dismissedIams.remove(message.messageId);
        }
        return dismissedIams;
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
     * Trigger logic
     * <p>
     * These methods mostly pass data to the Trigger Controller, but also cause the SDK to
     * re-evaluate messages to see if we should display a message now that the trigger
     * conditions have changed.
     */
    void addTriggers(Map<String, Object> newTriggers) {
        triggerController.addTriggers(newTriggers);
        evaluateInAppMessages();
    }

    void removeTriggersForKeys(Collection<String> keys) {
        triggerController.removeTriggersForKeys(keys);
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
