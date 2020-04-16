package com.onesignal;

import android.support.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public class OSInAppMessageOutcome {

    private static final String OUTCOME_NAME = "name";
    private static final String OUTCOME_WEIGHT = "weight";
    private static final String OUTCOME_UNIQUE = "unique";

    /**
     * Outcome key for action
     */
    private String name;
    private float weight;
    private boolean unique;

    OSInAppMessageOutcome(@NonNull JSONObject json) throws JSONException {
        name = json.getString(OUTCOME_NAME);
        weight = json.has(OUTCOME_WEIGHT) ? (float) json.getDouble(OUTCOME_WEIGHT) : 0;
        unique = json.has(OUTCOME_UNIQUE) && json.getBoolean(OUTCOME_UNIQUE);
    }

    public JSONObject toJSONObject() {
        JSONObject mainObj = new JSONObject();
        try {
            mainObj.put(OUTCOME_NAME, name);
            mainObj.put(OUTCOME_WEIGHT, weight);
            mainObj.put(OUTCOME_UNIQUE, unique);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return mainObj;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public float getWeight() {
        return weight;
    }

    public void setWeight(float weight) {
        this.weight = weight;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    @Override
    public String toString() {
        return "OSInAppMessageOutcome{" +
                "name='" + name + '\'' +
                ", weight=" + weight +
                ", unique=" + unique +
                '}';
    }
}