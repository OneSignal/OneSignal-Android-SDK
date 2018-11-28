package com.onesignal;

import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import com.onesignal.OSDynamicTriggerController.OSDynamicTriggerControllerObserver;

class OSInAppMessageController implements OSDynamicTriggerControllerObserver {
    private static OSInAppMessageController sharedInstance;

    OSTriggerController triggerController;
    private ArrayList<OSInAppMessage> messages;
    private final ArrayList<OSInAppMessage> messageDisplayQueue;

    static OSInAppMessageController getController() {
        if (sharedInstance == null) {
            sharedInstance = new OSInAppMessageController();
        }

        return sharedInstance;
    }

    private OSInAppMessageController() {
        messages = new ArrayList<>();
        messageDisplayQueue = new ArrayList<>();
        triggerController = new OSTriggerController(this);
    }

    void onSessionReceivedMessageJSON(JSONArray json) {
        ArrayList<OSInAppMessage> newMessages = new ArrayList<>();

        try {
            for (int i = 0; i < json.length(); i++) {
                JSONObject messageJson = json.getJSONObject(i);

                OSInAppMessage message = new OSInAppMessage(messageJson);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        evaluateInAppMessages();
    }

    private void evaluateInAppMessages() {
        for (OSInAppMessage message : messages) {
            if (triggerController.evaluateMessageTriggers(message)) {
                // message should be shown

                messageCanBeDisplayed(message);
            }
        }
    }

    private void messageCanBeDisplayed(OSInAppMessage message) {
        synchronized (messageDisplayQueue) {
            messageDisplayQueue.add(message);

            if (messageDisplayQueue.size() > 1) {
                // means we are already displaying a message
                // this message will be displayed afterwards
                return;
            }

            displayMessage(message);
        }
    }

    private void displayMessage(OSInAppMessage message) {
        // TODO: UI presentation logic
    }

    @Override
    public void messageTriggerConditionChanged() {

        // This method is called when a time-based trigger timer fires, meaning the message can
        // probably be shown now. So the current message conditions should be re-evaluated
        evaluateInAppMessages();
    }

    /**
     * Trigger logic
     *
     * These methods mostly pass data to the Trigger Controller, but also cause the SDK to
     * re-evaluate messages to see if we should display a message now that the trigger
     * conditions have changed.
     */
    void addTriggers(Map<String, Object> newTriggers) {
        triggerController.addTriggers(newTriggers);
    }

    void removeTriggersForKeys(Collection<String> keys) {
        triggerController.removeTriggersForKeys(keys);
    }

    @Nullable
    Object getTriggerValue(String key) {
        return triggerController.getTriggerValue(key);
    }
}
