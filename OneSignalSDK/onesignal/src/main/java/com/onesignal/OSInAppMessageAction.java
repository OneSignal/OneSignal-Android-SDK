package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONObject;

public class OSInAppMessageAction {

    /** The unique identifier for this action */
    @NonNull
    public String actionId;

    /** An optional URL that opens when the action takes place */
    @Nullable
    public String actionUrl;

    /** Determines where the URL is opened, ie. Safari */
    @Nullable
    public OSInAppMessageActionUrlType urlTarget;

    boolean closesMessage;

    /** Contains additional metadata for each action, currently not implemented */
    @Nullable
    public JSONObject additionalData;

    OSInAppMessageAction(@NonNull JSONObject json) {
        actionId = json.optString("action_id", null);
        actionUrl = json.optString("url", null);
        urlTarget = OSInAppMessageActionUrlType.fromString(json.optString("url_target", null));
        if (urlTarget == null)
            urlTarget = OSInAppMessageActionUrlType.IN_APP_WEBVIEW;

        closesMessage = json.optBoolean("close", true);
        additionalData = json.optJSONObject("data");
    }

    /**
     * An enumeration of the possible places action URL's can be loaded,
     * such as an in-app webview
     */
    public enum OSInAppMessageActionUrlType {
        // Opens in an in-app webview
        IN_APP_WEBVIEW("webview"),

        // Moves app to background and opens URL in browser
        BROWSER("browser"),

        // Loads the URL on the in-app message webview itself
        REPLACE_CONTENT("replacement");

        private String text;

        OSInAppMessageActionUrlType(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return this.text;
        }

        public static OSInAppMessageActionUrlType fromString(String text) {
            for (OSInAppMessageActionUrlType type : OSInAppMessageActionUrlType.values()) {
                if (type.text.equalsIgnoreCase(text))
                    return type;
            }

            return null;
        }
    }
}