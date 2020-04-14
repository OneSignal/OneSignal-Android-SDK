package com.onesignal.outcomes.model;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public class OSOutcomeEventParams {

    private static final String OUTCOME_ID = "id";
    private static final String OUTCOME_SOURCES = "sources";
    private static final String WEIGHT = "weight";
    private static final String TIMESTAMP = "timestamp";

    @NonNull
    private String outcomeId;
    @Nullable
    private OSOutcomeSource outcomeSource;
    // This field is optional, defaults to zero
    private Float weight;
    // This field is optional.
    private long timestamp;

    public OSOutcomeEventParams(@NonNull String outcomeId, @Nullable OSOutcomeSource outcomeSource, float weight) {
        this(outcomeId, outcomeSource, weight, 0);
    }

    public OSOutcomeEventParams(@NonNull String outcomeId, @Nullable OSOutcomeSource outcomeSource, float weight, long timestamp) {
        this.outcomeId = outcomeId;
        this.outcomeSource = outcomeSource;
        this.weight = weight;
        this.timestamp = timestamp;
    }

    public JSONObject toJSONObject() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(OUTCOME_ID, outcomeId);
        if (outcomeSource != null)
            json.put(OUTCOME_SOURCES, outcomeSource.toJSONObject());
        if (weight > 0)
            json.put(WEIGHT, weight);
        if (timestamp > 0)
            json.put(TIMESTAMP, timestamp);
        return json;
    }

    public String getOutcomeId() {
        return outcomeId;
    }

    public OSOutcomeSource getOutcomeSource() {
        return outcomeSource;
    }

    public Float getWeight() {
        return weight;
    }

    public void setWeight(float weight) {
        this.weight = weight;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isUnattributed() {
        return outcomeSource == null || outcomeSource.getDirectBody() == null && outcomeSource.getIndirectBody() == null;
    }

    @Override
    public String toString() {
        return "OSOutcomeEventParams{" +
                "outcomeId='" + outcomeId + '\'' +
                ", outcomeSource=" + outcomeSource +
                ", weight=" + weight +
                ", timestamp=" + timestamp +
                '}';
    }
}
