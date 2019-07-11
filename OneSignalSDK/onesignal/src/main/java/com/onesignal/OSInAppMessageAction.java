package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public class OSInAppMessageAction {
    /** UUID assigned by OneSignal for internal use.
     * Package-private to track which element was tapped to report to the OneSignal dashboard. */
    @NonNull
    String clickId;

    /** An optional click name entered defined by the app developer when creating the IAM */
    @Nullable
    public String clickName;

    /** Determines where the URL is opened, ie. Default browser. */
    @Nullable
    public OSInAppMessageActionUrlType urlTarget;

    /** An optional URL that opens when the action takes place */
    @Nullable
    public String clickUrl;

    /** Determines if this was the first action taken on the in app message */
    public boolean firstClick;

    /** Determines if tapping on the element should close the In-App Message. */
    public boolean closesMessage;

    OSInAppMessageAction(@NonNull JSONObject json) {
        clickId = json.optString("id", null);
        clickName = json.optString("name", null);
        clickUrl = json.optString("url", null);
        urlTarget = OSInAppMessageActionUrlType.fromString(json.optString("url_target", null));
        if (urlTarget == null)
            urlTarget = OSInAppMessageActionUrlType.IN_APP_WEBVIEW;

        closesMessage = json.optBoolean("close", true);
    }

    public JSONObject toJSONObject() {
        JSONObject mainObj = new JSONObject();
        try {
            mainObj.put("click_name", clickName);
            mainObj.put("click_url", clickUrl);
            mainObj.put("first_click", firstClick);
            mainObj.put("closes_message", closesMessage);

            // Omitted for now until necessary
//            if (urlTarget != null)
//                mainObj.put("url_target", urlTarget.toJSONObject());

        }
        catch(JSONException e) {
            e.printStackTrace();
        }

        return mainObj;
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

        public JSONObject toJSONObject() {
            JSONObject mainObj = new JSONObject();
            try {
                mainObj.put("url_type", text);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return mainObj;
        }
    }
}