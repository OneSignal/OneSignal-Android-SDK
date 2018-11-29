package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;

public class OSInAppMessageAction {

    /** The unique identifier for this action */
    @NonNull
    public String actionId;

    /** An optional URL that opens when the action takes place */
    @Nullable
    public URL actionUrl;

    /** Determines where the URL is opened, ie. Safari */
    @Nullable
    public OSInAppMessageActionUrlType urlTarget;

    @NonNull
    public boolean closesMessage;

    public OSInAppMessageAction() {}

    public OSInAppMessageAction(JSONObject json) throws JSONException {
        actionId = json.getString("action_id");

        if (json.has("url")) {
            try {
                actionUrl = new URL(json.getString("url"));
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

        if (json.has("url_target"))
            urlTarget = OSInAppMessageActionUrlType.fromString(json.getString("url_target"));

        closesMessage = json.getBoolean("close");
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
                if (type.text.equalsIgnoreCase(text)) {
                    return type;
                }
            }

            return null;
        }
    }
}