package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

class OutcomeParams {

    private static final String WEIGHT = "weight";

    private HashMap<String, Object> params;

    OutcomeParams(Builder builder) {
        this.params = builder.params;
    }

    void addParamsToJson(JSONObject jsonObject) {
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            try {
                jsonObject.put(entry.getKey(), entry.getValue());
            } catch (JSONException e) {
                OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating outcome params Failed.", e);
            }
        }
    }

    String getAsJSONString() {
        JSONObject jsonObject = new JSONObject();
        addParamsToJson(jsonObject);
        return jsonObject.toString();
    }

    static class Builder {

        private HashMap<String, Object> params = new HashMap<>();

        static Builder newInstance() {
            return new Builder();
        }

        Builder setWeight(@Nullable Float weight) {
            if (weight != null)
                params.put(WEIGHT, weight);
            return this;
        }

        Builder setJsonString(@Nullable String jsonString) {
            if (jsonString != null && !jsonString.isEmpty()) {
                try {
                    return setJson(new JSONObject(jsonString));
                } catch (JSONException e) {
                    OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating outcome params setJsonString Failed.", e);
                }
            }
            return this;
        }

        Builder setJson(@Nullable JSONObject jsonObject) {
            if (jsonObject != null) {
                try {
                    addToMap(jsonObject);
                } catch (JSONException e) {
                    OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating outcome params setJson Failed.", e);
                }
            }
            return this;
        }

        private void addToMap(@NonNull JSONObject jsonObject) throws JSONException {
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = jsonObject.get(key);
                params.put(key, value);
            }
        }

        OutcomeParams build() {
            return new OutcomeParams(this);
        }
    }
}