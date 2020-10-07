package com.onesignal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static com.onesignal.OSInAppMessageLocationPrompt.LOCATION_PROMPT_KEY;

public class OSInAppMessageAction {

    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String URL = "url";
    private static final String PAGE_ID = "page_id";
    private static final String URL_TARGET = "url_target";
    private static final String CLOSE = "close";
    private static final String CLICK_NAME = "click_name";
    private static final String CLICK_URL = "click_url";
    private static final String FIRST_CLICK = "first_click";
    private static final String CLOSES_MESSAGE = "closes_message";
    private static final String OUTCOMES = "outcomes";
    private static final String TAGS = "tags";
    private static final String PROMPTS = "prompts";

    /**
     * UUID assigned by OneSignal for internal use.
     * Package-private to track which element was tapped to report to the OneSignal dashboard.
     */
    @NonNull
    private String clickId;

    /**
     * An optional click name entered defined by the app developer when creating the IAM
     */
    @Nullable
    private  String clickName;

    /**
     * Determines where the URL is opened, ie. Default browser.
     */
    @Nullable
    private OSInAppMessageActionUrlType urlTarget;

    /**
     * An optional URL that opens when the action takes place
     */
    @Nullable
    private String clickUrl;

    /**
     * UUID for the page in an IAM Carousel
     */
    @Nullable
    private String pageId;

    /**
     * Outcome for action
     */
    @NonNull
    private List<OSInAppMessageOutcome> outcomes = new ArrayList<>();

    /**
     * Prompts for action
     */
    @NonNull
    private List<OSInAppMessagePrompt> prompts = new ArrayList<>();

    /**
     * Tags for action
     */
    private OSInAppMessageTag tags;

    /**
     * Determines if this was the first action taken on the in app message
     */
    private boolean firstClick;

    /**
     * Determines if tapping on the element should close the In-App Message.
     */
    private boolean closesMessage;

    OSInAppMessageAction(@NonNull JSONObject json) throws JSONException {
        clickId = json.optString(ID, null);
        clickName = json.optString(NAME, null);
        clickUrl = json.optString(URL, null);
        pageId = json.optString(PAGE_ID, null);
        urlTarget = OSInAppMessageActionUrlType.fromString(json.optString(URL_TARGET, null));
        if (urlTarget == null)
            urlTarget = OSInAppMessageActionUrlType.IN_APP_WEBVIEW;

        closesMessage = json.optBoolean(CLOSE, true);

        if (json.has(OUTCOMES))
            parseOutcomes(json);

        if (json.has(TAGS))
            tags = new OSInAppMessageTag(json.getJSONObject(TAGS));

        if (json.has(PROMPTS))
            parsePrompts(json);
    }

    private void parseOutcomes(JSONObject json) throws JSONException {
        JSONArray outcomesJsonArray = json.getJSONArray(OUTCOMES);
        for (int i = 0; i < outcomesJsonArray.length(); i++) {
            outcomes.add(new OSInAppMessageOutcome((JSONObject) outcomesJsonArray.get(i)));
        }
    }

    private void parsePrompts(JSONObject json) throws JSONException {
        JSONArray promptsJsonArray = json.getJSONArray(PROMPTS);
        for (int i = 0; i < promptsJsonArray.length(); i++) {
            if (promptsJsonArray.get(i).equals(LOCATION_PROMPT_KEY) ) {
                prompts.add(new OSInAppMessageLocationPrompt());
            }
        }
    }

    @NonNull
    String getClickId() {
        return clickId;
    }

    @Nullable
    public String getClickName() {
        return clickName;
    }

    @NonNull
    String getPageId() {
        return pageId;
    }

    @Nullable
    public OSInAppMessageActionUrlType getUrlTarget() {
        return urlTarget;
    }

    @Nullable
    public String getClickUrl() {
        return clickUrl;
    }

    @NonNull
    public List<OSInAppMessageOutcome> getOutcomes() {
        return outcomes;
    }

    @NonNull
    public List<OSInAppMessagePrompt> getPrompts() {
        return prompts;
    }

    public OSInAppMessageTag getTags() {
        return tags;
    }

    public boolean isFirstClick() {
        return firstClick;
    }

    void setFirstClick(boolean firstClick) {
        this.firstClick = firstClick;
    }

    public boolean doesCloseMessage() {
        return closesMessage;
    }

    public JSONObject toJSONObject() {
        JSONObject mainObj = new JSONObject();
        try {
            mainObj.put(CLICK_NAME, clickName);
            mainObj.put(CLICK_URL, clickUrl);
            mainObj.put(FIRST_CLICK, firstClick);
            mainObj.put(CLOSES_MESSAGE, closesMessage);

            JSONArray outcomesJson = new JSONArray();
            for (OSInAppMessageOutcome outcome : outcomes)
                outcomesJson.put(outcome.toJSONObject());

            mainObj.put(OUTCOMES, outcomesJson);

            if (tags != null)
                mainObj.put(TAGS, tags.toJSONObject());
            // Omitted for now until necessary
//            if (urlTarget != null)
//                mainObj.put("url_target", urlTarget.toJSONObject());

        } catch (JSONException e) {
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