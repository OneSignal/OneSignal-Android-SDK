package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONObject;

public class OSInAppMessageAction {

    public enum ClickType {
        BUTTON("button"), IMAGE("image");

        private String text;

        ClickType(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return this.text;
        }

        public static @Nullable ClickType fromString(String text) {
            for (ClickType type : ClickType.values()) {
                if (type.text.equalsIgnoreCase(text))
                    return type;
            }
            return null;
        }
    }

    /** The type of element that was click, can be a button or image. */
    @NonNull
    public ClickType clickType;

    /** UUID assigned by OneSignal for internal use.
     * Package-private to track which element was tapped to report to the OneSignal dashboard. */
    @NonNull
    String clickId;

    /** An optional click name entered defined by the app developer when creating the IAM */
    @Nullable
    public String clickName;

    /** An optional URL that opens when the action takes place */
    @Nullable
    public String clickUrl;

    /** Determines if this was the first action taken on the in app message */
    public boolean firstClick;

    /** Determines where the URL is opened, ie. Default browser. */
    @Nullable
    public OSInAppMessageActionUrlType urlTarget;

    /** Determines if tapping on the element should close the In-App Message. */
    public boolean closesMessage;

    OSInAppMessageAction(@NonNull JSONObject json) {
        clickType = ClickType.fromString(json.optString("click_type"));
        clickId = json.optString("click_id", null);
        clickName = json.optString("click_name", null);
        clickUrl = json.optString("url", null);
        urlTarget = OSInAppMessageActionUrlType.fromString(json.optString("url_target", null));
        if (urlTarget == null)
            urlTarget = OSInAppMessageActionUrlType.IN_APP_WEBVIEW;

        closesMessage = json.optBoolean("close", true);
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