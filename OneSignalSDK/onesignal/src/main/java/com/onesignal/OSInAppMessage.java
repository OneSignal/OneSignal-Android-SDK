package com.onesignal;

import android.support.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

class OSInAppMessage {

    private static final String IAM_ID = "id";
    private static final String IAM_VARIANTS = "variants";
    private static final String IAM_TRIGGERS = "triggers";
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
     * <p>
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

    //Last IAM display time in seconds
    private long lastDisplayTime = -1;
    //Current quantity of displays
    private int displaysQuantity = 0;
    //Quantity of displays limit
    private int displaysLimit = Integer.MAX_VALUE;
    //Delay between displays in seconds
    private long delay = 0;
    private double displayDuration;

    private boolean reDisplayEnabled = false;
    private boolean actionTaken;
    boolean isPreview;

    OSInAppMessage(boolean isPreview) {
        this.isPreview = isPreview;
    }

    OSInAppMessage(@NonNull String messageId, int displaysQuantity, long lastDisplayTime) {
        this.messageId = messageId;
        this.lastDisplayTime = lastDisplayTime;
        this.displaysQuantity = displaysQuantity;
    }

    OSInAppMessage(JSONObject json) throws JSONException {

        // initialize simple root properties
        this.messageId = json.getString(IAM_ID);
        this.variants = parseVariants(json.getJSONObject(IAM_VARIANTS));
        this.triggers = parseTriggerJson(json.getJSONArray(IAM_TRIGGERS));

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
        reDisplayEnabled = true;

        Object displayLimit = json.get(DISPLAY_LIMIT);
        Object displayDelay = json.get(DISPLAY_DELAY);

        if (displayLimit instanceof Integer)
            this.displaysLimit = (Integer) displayLimit;

        if (displayDelay instanceof Long)
            this.delay = (Long) displayDelay;
        else if (displayDelay instanceof Integer)
            this.delay = (Integer) displayDelay;
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
            json.put(DISPLAY_LIMIT, this.displaysLimit);
            json.put(DISPLAY_DELAY, this.delay);

            if (reDisplayEnabled)
                json.put(IAM_RE_DISPLAY, new JSONObject() {{
                    put(DISPLAY_LIMIT, displaysLimit);
                    put(DISPLAY_DELAY, delay);
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

    int getDisplaysQuantity() {
        return displaysQuantity;
    }

    void setDisplaysQuantity(int displaysQuantity) {
        this.displaysQuantity = displaysQuantity;
    }

    void increaseDisplayQuantity() {
        this.displaysQuantity++;
    }

    int getDisplaysLimit() {
        return displaysLimit;
    }

    long getDelay() {
        return delay;
    }

    long getLastDisplayTime() {
        return lastDisplayTime;
    }

    void setLastDisplayTime(long lastDisplayTime) {
        this.lastDisplayTime = lastDisplayTime;
    }

    boolean isReDisplayEnabled() {
        return reDisplayEnabled;
    }

    boolean isRemainingDisplays() {
        return displaysQuantity <= displaysLimit;
    }

    boolean isDelayTimeSatisfied() {
        if (lastDisplayTime == -1) {
            lastDisplayTime = new Date().getTime() / 1000; //Current time in seconds
            return true;
        }

        //Calculate gap between display times
        long diffInSeconds = new Date().getTime() / 1000 - lastDisplayTime;
        return diffInSeconds >= delay;
    }

    @Override
    public String toString() {
        return "OSInAppMessage{" +
                "messageId='" + messageId + '\'' +
                ", triggers=" + triggers +
                ", lastDisplayTime=" + lastDisplayTime +
                ", displaysQuantity=" + displaysQuantity +
                ", displaysLimit=" + displaysLimit +
                ", delay=" + delay +
                ", reDisplayEnabled=" + reDisplayEnabled +
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
