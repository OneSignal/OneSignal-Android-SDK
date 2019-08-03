package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Map;

class OSInAppMessageDummyController extends OSInAppMessageController {

    /**
     * In App Messaging is not supported for Android 4.3 and older devices
     * This is a dummy controller that will be used for Android 4.3 and older devices
     * All methods should be overridden and as empty as possible (few return exceptions)
     */
    OSInAppMessageDummyController() {

    }

    @Override
    void initWithCachedInAppMessages() { }

    @Override
    void receivedInAppMessageJson(@NonNull JSONArray json) throws JSONException { }

    @Override
    void onMessageActionOccurredOnMessage(@NonNull OSInAppMessage message, @NonNull JSONObject actionJson) { }

    @Override
    void onMessageActionOccurredOnPreview(@NonNull OSInAppMessage message, @NonNull JSONObject actionJson) { }

    @Override
    boolean isDisplayingInApp() { return false; }

    @Nullable
    @Override
    OSInAppMessage getCurrentDisplayedInAppMessage() { return null; }

    @Override
    void messageWasDismissed(@NonNull OSInAppMessage message) { }

    @Override
    void displayPreviewMessage(@NonNull String previewUUID) { }

    @Override
    public void messageTriggerConditionChanged() { }

    @Override
    void addTriggers(Map<String, Object> newTriggers) { }

    @Override
    void removeTriggersForKeys(Collection<String> keys) { }

    @Override
    void setInAppMessagingEnabled(boolean enabled) { }

    @Nullable
    @Override
    Object getTriggerValue(String key) { return null; }
}

