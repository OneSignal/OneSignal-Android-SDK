package com.onesignal;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

class OSInAppMessageDisplayStats {

    private static final String DISPLAY_LIMIT = "limit";
    private static final String DISPLAY_DELAY = "delay";

    //Last IAM display time in seconds
    private double lastDisplayTime = -1;
    //Current quantity of displays
    private int displayQuantity = 0;
    //Quantity of displays limit
    private int displayLimit = Integer.MAX_VALUE;
    //Delay between displays in seconds
    private double displayDelay = 0;

    private boolean redisplayEnabled = false;

    OSInAppMessageDisplayStats() {
    }

    OSInAppMessageDisplayStats(int displayQuantity, double lastDisplayTime) {
        this.displayQuantity = displayQuantity;
        this.lastDisplayTime = lastDisplayTime;
    }

    OSInAppMessageDisplayStats(JSONObject json) throws JSONException {
        this.redisplayEnabled = true;
        Object displayLimit = json.get(DISPLAY_LIMIT);
        Object displayDelay = json.get(DISPLAY_DELAY);

        if (displayLimit instanceof Integer)
            this.displayLimit = (Integer) displayLimit;

        if (displayDelay instanceof Double)
            this.displayDelay = (Double) displayDelay;
        else if (displayDelay instanceof Integer)
            this.displayDelay = (Integer) displayDelay;
    }

    void setDisplayStats(OSInAppMessageDisplayStats displayStats) {
        setLastDisplayTime(displayStats.getLastDisplayTime());
        setDisplayQuantity(displayStats.getDisplayQuantity());
    }

    double getLastDisplayTime() {
        return lastDisplayTime;
    }

    void setLastDisplayTime(double lastDisplayTime) {
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

    double getDisplayDelay() {
        return displayDelay;
    }

    void setDisplayDelay(double displayDelay) {
        this.displayDelay = displayDelay;
    }

    boolean shouldDisplayAgain() {
        return displayQuantity < displayLimit;
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
