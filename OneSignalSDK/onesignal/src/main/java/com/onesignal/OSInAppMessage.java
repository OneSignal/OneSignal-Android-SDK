package com.onesignal;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

class OSInAppMessage {

    private static final String IAM_ID = "id";
    private static final String IAM_VARIANTS = "variants";
    private static final String IAM_TRIGGERS = "triggers";
    private static final String IAM_REDISPLAY_STATS = "redisplay";
    private static final String DISPLAY_DURATION = "displayDuration";

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

    /**
     * IAM carousel pages viewed
     */
    @NonNull
    private Set<String> viewedPageIds = new HashSet<>();

    /**
     * Reference to redisplay properties
     */
    private OSInAppMessageRedisplayStats redisplayStats = new OSInAppMessageRedisplayStats();

    private double displayDuration;
    private boolean displayedInSession = false;
    private boolean triggerChanged = false;
    private boolean actionTaken;
    boolean isPreview;

    OSInAppMessage(boolean isPreview) {
        this.isPreview = isPreview;
    }

    OSInAppMessage(@NonNull String messageId, @NonNull Set<String> clickIds, boolean displayedInSession, OSInAppMessageRedisplayStats redisplayStats) {
        this.messageId = messageId;
        this.clickedClickIds = clickIds;
        this.displayedInSession = displayedInSession;
        this.redisplayStats = redisplayStats;
    }

    OSInAppMessage(JSONObject json) throws JSONException {
        // initialize simple root properties
        this.messageId = json.getString(IAM_ID);
        this.variants = parseVariants(json.getJSONObject(IAM_VARIANTS));
        this.triggers = parseTriggerJson(json.getJSONArray(IAM_TRIGGERS));
        this.clickedClickIds = new HashSet<>();

        if (json.has(IAM_REDISPLAY_STATS))
            this.redisplayStats = new OSInAppMessageRedisplayStats(json.getJSONObject(IAM_REDISPLAY_STATS));
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
            json.put(IAM_REDISPLAY_STATS, this.redisplayStats.toJSONObject());

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

    boolean isTriggerChanged() {
        return triggerChanged;
    }

    void setTriggerChanged(boolean triggerChanged) {
        this.triggerChanged = triggerChanged;
    }

    public boolean isDisplayedInSession() {
        return displayedInSession;
    }

    public void setDisplayedInSession(boolean displayedInSession) {
        this.displayedInSession = displayedInSession;
    }

    @NonNull
    Set<String> getClickedClickIds() {
        return clickedClickIds;
    }

    boolean isClickAvailable(String clickId) {
        return !clickedClickIds.contains(clickId);
    }

    void clearClickIds() {
        clickedClickIds.clear();
    }

    void addClickId(String clickId) {
        clickedClickIds.add(clickId);
    }

    @NonNull
    Set<String> getViewedPageIds() { return viewedPageIds; }

    void clearPageIds() { viewedPageIds.clear(); }

    void addPageId(String pageId) { viewedPageIds.add(pageId); }

    OSInAppMessageRedisplayStats getRedisplayStats() {
        return redisplayStats;
    }

    void setRedisplayStats(int displayQuantity, long lastDisplayTime) {
        this.redisplayStats = new OSInAppMessageRedisplayStats(displayQuantity, lastDisplayTime);
    }

    @Override
    public String toString() {
        return "OSInAppMessage{" +
                "messageId='" + messageId + '\'' +
                ", triggers=" + triggers +
                ", clickedClickIds=" + clickedClickIds +
                ", displayStats=" + redisplayStats +
                ", actionTaken=" + actionTaken +
                ", isPreview=" + isPreview +
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
