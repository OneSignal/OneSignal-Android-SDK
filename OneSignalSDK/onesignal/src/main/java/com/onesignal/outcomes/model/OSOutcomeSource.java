package com.onesignal.outcomes.model;

import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public class OSOutcomeSource {

    private static final String DIRECT = "direct";
    private static final String INDIRECT = "indirect";

    @Nullable
    private OSOutcomeSourceBody directBody;
    @Nullable
    private OSOutcomeSourceBody indirectBody;

    public OSOutcomeSource(@Nullable OSOutcomeSourceBody directBody, @Nullable OSOutcomeSourceBody indirectBody) {
        this.directBody = directBody;
        this.indirectBody = indirectBody;
    }

    public JSONObject toJSONObject() throws JSONException {
        JSONObject json = new JSONObject();
        if (directBody != null)
            json.put(DIRECT, directBody.toJSONObject());
        if (indirectBody != null)
            json.put(INDIRECT, indirectBody.toJSONObject());
        return json;
    }

    public OSOutcomeSourceBody getDirectBody() {
        return directBody;
    }

    public OSOutcomeSource setDirectBody(@Nullable OSOutcomeSourceBody directBody) {
        this.directBody = directBody;
        return this;
    }

    public OSOutcomeSourceBody getIndirectBody() {
        return indirectBody;
    }

    public OSOutcomeSource setIndirectBody(@Nullable OSOutcomeSourceBody indirectBody) {
        this.indirectBody = indirectBody;
        return this;
    }

    @Override
    public String toString() {
        return "OSOutcomeSource{" +
                "directBody=" + directBody +
                ", indirectBody=" + indirectBody +
                '}';
    }
}
