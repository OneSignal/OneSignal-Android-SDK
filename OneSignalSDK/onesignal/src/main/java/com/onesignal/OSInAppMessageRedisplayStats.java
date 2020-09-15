package com.onesignal;

import org.json.JSONException;
import org.json.JSONObject;

class OSInAppMessageRedisplayStats {

    private static final String DISPLAY_LIMIT = "limit";
    private static final String DISPLAY_DELAY = "delay";

    // Last IAM display time in seconds
    private long lastDisplayTime = -1;
    // Current quantity of displays
    private int displayQuantity = 0;
    // Quantity of displays limit
    private int displayLimit = 1;
    // Delay between displays in seconds
    private long displayDelay = 0;

    private boolean redisplayEnabled = false;

    OSInAppMessageRedisplayStats() {
    }

    OSInAppMessageRedisplayStats(int displayQuantity, long lastDisplayTime) {
        this.displayQuantity = displayQuantity;
        this.lastDisplayTime = lastDisplayTime;
    }

    OSInAppMessageRedisplayStats(JSONObject json) throws JSONException {
        this.redisplayEnabled = true;
        Object displayLimit = json.get(DISPLAY_LIMIT);
        Object displayDelay = json.get(DISPLAY_DELAY);

        if (displayLimit instanceof Integer)
            this.displayLimit = (Integer) displayLimit;

        if (displayDelay instanceof Long)
            this.displayDelay = (Long) displayDelay;
        else if (displayDelay instanceof Integer)
            this.displayDelay = (Integer) displayDelay;
    }

    void setDisplayStats(OSInAppMessageRedisplayStats displayStats) {
        setLastDisplayTime(displayStats.getLastDisplayTime());
        setDisplayQuantity(displayStats.getDisplayQuantity());
    }

    long getLastDisplayTime() {
        return lastDisplayTime;
    }

    void setLastDisplayTime(long lastDisplayTime) {
        this.lastDisplayTime = lastDisplayTime;
    }

    void incrementDisplayQuantity() {
        this.displayQuantity++;
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

    void setDisplayLimit(int displayLimit) {
        this.displayLimit = displayLimit;
    }

    long getDisplayDelay() {
        return displayDelay;
    }

    void setDisplayDelay(long displayDelay) {
        this.displayDelay = displayDelay;
    }

    boolean shouldDisplayAgain() {
        boolean result = displayQuantity < displayLimit;
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "OSInAppMessage shouldDisplayAgain: " + result);
        return result;
    }

    boolean isDelayTimeSatisfied() {
        if (lastDisplayTime < 0)
            return true;

        long currentTimeInSeconds = System.currentTimeMillis() / 1000;
        // Calculate gap between display times
        long diffInSeconds = currentTimeInSeconds - lastDisplayTime;
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "OSInAppMessage lastDisplayTime: " + lastDisplayTime +
                " currentTimeInSeconds: " + currentTimeInSeconds + " diffInSeconds: " + diffInSeconds + " displayDelay: " + displayDelay);
        return diffInSeconds >= displayDelay;
    }

    public boolean isRedisplayEnabled() {
        return redisplayEnabled;
    }

    JSONObject toJSONObject() {
        JSONObject json = new JSONObject();

        try {
            json.put(DISPLAY_LIMIT, displayLimit);
            json.put(DISPLAY_DELAY, displayDelay);
        } catch (JSONException exception) {
            exception.printStackTrace();
        }

        return json;
    }

    @Override
    public String toString() {
        return "OSInAppMessageDisplayStats{" +
                "lastDisplayTime=" + lastDisplayTime +
                ", displayQuantity=" + displayQuantity +
                ", displayLimit=" + displayLimit +
                ", displayDelay=" + displayDelay +
                '}';
    }
}
