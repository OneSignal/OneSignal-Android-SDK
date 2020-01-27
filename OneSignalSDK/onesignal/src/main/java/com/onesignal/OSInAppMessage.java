package com.onesignal;

import android.support.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

class OSInAppMessage {

    private static final String IAM_ID = "id";
    private static final String IAM_VARIANTS = "variants";
    private static final String IAM_TRIGGERS = "triggers";
    private static final String IAM_CLICK_IDS = "click_ids";
    private static final String IAM_RE_DISPLAY = "redisplay";
    private static final String DISPLAY_DURATION = "display_duration";
    private static final String DISPLAY_LIMIT = "limit";
    private static final String DISPLAY_DELAY = "delay";

    /**
     * The unique identifier for this in-app message
     */
    @NonNull
    public String messageId;

    /**
     * Allows in-app messages to use multiple language variants, or to have variations between
     * different device types (ie. a different image for phones vs. tablets, etc.).
     *
     * An example: {'ios' : {'en' : 'wfgkv-...', 'es' : '56ytdygd...' }}
     */
    @NonNull
    public HashMap<String, HashMap<String, String>> variants;

    /**
     * An array of arrays of triggers. The outer array represents AND conditions,
     * while the inner array represents AND conditions.
     */
    @NonNull
    public ArrayList<ArrayList<OSTrigger>> triggers;

    /**
     * IAM clicks associated to this IAM
     */
    @NonNull
    private Set<String> clickedClickIds;

    //Last IAM display time in seconds
    private double lastDisplayTime = -1;
    //Current quantity of displays
    private int displayQuantity = 0;
    //Quantity of displays limit
    private int displayLimit = Integer.MAX_VALUE;
    //Delay between displays in seconds
    private double displayDelay = 0;
    private double displayDuration;

    private boolean redisplayEnabled = false;
    private boolean actionTaken;
    boolean isPreview;

    OSInAppMessage(boolean isPreview) {
        this.isPreview = isPreview;
    }

    OSInAppMessage(@NonNull String messageId, int displayQuantity, double lastDisplayTime, Set<String> clickIds) {
        this.messageId = messageId;
        this.lastDisplayTime = lastDisplayTime;
        this.displayQuantity = displayQuantity;
        this.clickedClickIds = clickIds;
    }

    OSInAppMessage(JSONObject json) throws JSONException {

        // initialize simple root properties
        this.messageId = json.getString(IAM_ID);
        this.variants = parseVariants(json.getJSONObject(IAM_VARIANTS));
        this.triggers = parseTriggerJson(json.getJSONArray(IAM_TRIGGERS));
        this.clickedClickIds = new HashSet<>();

        if (json.has(IAM_RE_DISPLAY))
            parseReDisplayJSON(json.getJSONObject(IAM_RE_DISPLAY));
    }

    private HashMap<String, HashMap<String, String>> parseVariants(JSONObject json) throws JSONException {
        HashMap<String, HashMap<String, String>> variantTypes = new HashMap<>();

        Iterator<String> keyIterator = json.keys();
        while (keyIterator.hasNext()) {
            String variantType = keyIterator.next();
            JSONObject variant = json.getJSONObject(variantType);
            HashMap<String, String> variantMap = new HashMap<>();

            Iterator<String> variantIterator = variant.keys();
            while (variantIterator.hasNext()) {
                String languageType = variantIterator.next();
                variantMap.put(languageType, variant.getString(languageType));
            }

            variantTypes.put(variantType, variantMap);
        }

        return variantTypes;
    }

    protected ArrayList<ArrayList<OSTrigger>> parseTriggerJson(JSONArray triggersJson) throws JSONException {
        // initialize triggers
        ArrayList<ArrayList<OSTrigger>> parsedTriggers = new ArrayList<>();

        for (int i = 0; i < triggersJson.length(); i++) {
            JSONArray ands = triggersJson.getJSONArray(i);
            ArrayList<OSTrigger> converted = new ArrayList<>();
            for (int j = 0; j < ands.length(); j++) {
                OSTrigger trigger = new OSTrigger(ands.getJSONObject(j));
                converted.add(trigger);
            }
            parsedTriggers.add(converted);
        }

        return parsedTriggers;
    }

    private void parseReDisplayJSON(JSONObject json) throws JSONException {
        redisplayEnabled = true;

        Object displayLimit = json.get(DISPLAY_LIMIT);
        Object displayDelay = json.get(DISPLAY_DELAY);

        if (displayLimit instanceof Integer)
            this.displayLimit = (Integer) displayLimit;

        if (displayDelay instanceof Double)
            this.displayDelay = (Double) displayDelay;
        else if (displayDelay instanceof Integer)
            this.displayDelay = (Integer) displayDelay;
    }

    JSONObject toJSONObject() {
        JSONObject json = new JSONObject();

        try {
            json.put(IAM_ID, this.messageId);

            JSONObject variants = new JSONObject();
            for (String key : this.variants.keySet()) {
                HashMap<String, String> variant = this.variants.get(key);
                JSONObject converted = new JSONObject();

                for (String variantKey : variant.keySet())
                    converted.put(variantKey, variant.get(variantKey));

                variants.put(key, converted);
            }

            json.put(IAM_VARIANTS, variants);
            json.put(DISPLAY_DURATION, this.displayDuration);
            json.put(DISPLAY_LIMIT, this.displayLimit);
            json.put(DISPLAY_DELAY, this.displayDelay);

            if (redisplayEnabled)
                json.put(IAM_RE_DISPLAY, new JSONObject() {{
                    put(DISPLAY_LIMIT, displayLimit);
                    put(DISPLAY_DELAY, displayDelay);
                }});

            JSONArray orConditions = new JSONArray();
            for (ArrayList<OSTrigger> andArray : this.triggers) {
                JSONArray andConditions = new JSONArray();

                for (OSTrigger trigger : andArray)
                    andConditions.put(trigger.toJSONObject());

                orConditions.put(andConditions);
            }

            json.put(IAM_TRIGGERS, orConditions);

        } catch (JSONException exception) {
            exception.printStackTrace();
        }

        return json;
    }

    /**
     * Called when an action is taken to track uniqueness
     *
     * @return true if action taken was unique
     */
    boolean takeActionAsUnique() {
        if (actionTaken)
            return false;
        return actionTaken = true;
    }

    double getDisplayDuration() {
        return displayDuration;
    }

    void setDisplayDuration(double displayDuration) {
        this.displayDuration = displayDuration;
    }

    int getDisplayQuantity() {
        return displayQuantity;
    }

    void setDisplayQuantity(int displayQuantity) {
        this.displayQuantity = displayQuantity;
    }

    int getDisplayLimit() {
        return displayLimit;
    }

    void incrementDisplayQuantity() {
        this.displayQuantity++;
    }

    double getDisplayDelay() {
        return displayDelay;
    }

    double getLastDisplayTime() {
        return lastDisplayTime;
    }

    void setLastDisplayTime(double lastDisplayTime) {
        this.lastDisplayTime = lastDisplayTime;
    }

    boolean isRedisplayEnabled() {
        return redisplayEnabled;
    }

    boolean shouldDisplayAgain() {
        return displayQuantity <= displayLimit;
    }

    boolean isDelayTimeSatisfied() {
        if (lastDisplayTime < 0) {
            lastDisplayTime = new Date().getTime() / 1000; //Current time in seconds
            return true;
        }

        double currentTimeInSeconds = new Date().getTime() / 1000;
        //Calculate gap between display times
        double diffInSeconds = currentTimeInSeconds - lastDisplayTime;
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "OSInAppMessage lastDisplayTime: " + lastDisplayTime +
                " currentTimeInSeconds: " + currentTimeInSeconds + " diffInSeconds: " + diffInSeconds + " displayDelay: " + displayDelay);
        return diffInSeconds >= displayDelay;
    }

    @NonNull
    Set<String> getClickedClickIds() {
        return clickedClickIds;
    }

    boolean isClickAvailable(String clickId) {
        return redisplayEnabled && !clickedClickIds.contains(clickId);
    }

    void clearClickIds() {
        clickedClickIds.clear();
    }

    void addClickId(String clickId) {
        clickedClickIds.add(clickId);
    }

    @Override
    public String toString() {
        return "OSInAppMessage{" +
                "messageId='" + messageId + '\'' +
                ", triggers=" + triggers +
                ", lastDisplayTime=" + lastDisplayTime +
                ", displayQuantity=" + displayQuantity +
                ", displayLimit=" + displayLimit +
                ", displayDelay=" + displayDelay +
                ", redisplayEnabled=" + redisplayEnabled +
                ", isPreview=" + isPreview +
                ", clickIds=" + clickedClickIds.toString() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OSInAppMessage that = (OSInAppMessage) o;
        return messageId.equals(that.messageId);
    }

    @Override
    public int hashCode() {
        int result = messageId.hashCode();
        return result;
    }
}
