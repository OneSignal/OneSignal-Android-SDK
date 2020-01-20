package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

class OSTrigger {

    /**
     * An enumeration of the operators triggers can use
     */
    public enum OSTriggerOperator {
        GREATER_THAN("greater"),
        LESS_THAN("less"),
        EQUAL_TO("equal"),
        NOT_EQUAL_TO("not_equal"),
        LESS_THAN_OR_EQUAL_TO("less_or_equal"),
        GREATER_THAN_OR_EQUAL_TO("greater_or_equal"),
        EXISTS("exists"),
        NOT_EXISTS("not_exists"),
        CONTAINS("in");

        private String text;

        OSTriggerOperator(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return this.text;
        }

        public static @NonNull OSTriggerOperator fromString(String text) {
            for (OSTriggerOperator type : OSTriggerOperator.values()) {
                if (type.text.equalsIgnoreCase(text))
                    return type;
            }

            return EQUAL_TO;
        }

        public boolean checksEquality() {
            return this == EQUAL_TO || this == NOT_EQUAL_TO;
        }
    }

    public enum OSTriggerKind {
        TIME_SINCE_LAST_IN_APP("min_time_since"),
        SESSION_TIME("session_time"),
        CUSTOM("custom"),
        UNKNOWN("unknown");

        private String value;

        OSTriggerKind(String value) {
            this.value = value;
        }

        public String toString() {
            return this.value;
        }

        public static @NonNull OSTriggerKind fromString(String value) {
            for (OSTriggerKind type : OSTriggerKind.values()) {
                if (type.value.equalsIgnoreCase(value))
                    return type;
            }
            return UNKNOWN;
        }
    }

    // Position.valueOf(jsonObject.optString("displayLocation", "FULL_SCREEN").toUpperCase());

    /**
     * The unique identifier for this trigger, to help avoid scheduling duplicate timers and so on
     */
    @NonNull
    String triggerId;

    /**
     * Kind of trigger; session time, time since last in app, or custom.
     */
    @NonNull
    public OSTriggerKind kind;

    /**
     * The property that this trigger operates on, such as 'game_score'
     */
    @Nullable
    public String property;

    /**
     * The type of operator used to perform the logical equivalence/comparison on,
     * such as > or <=
     */
    @NonNull
    public OSTriggerOperator operatorType;

    /**
     * Most comparison-based operators have a value to allow for triggers
     * such as game_score > 30
     */
    @Nullable
    public Object value;

    OSTrigger(JSONObject json) throws JSONException {
        this.triggerId = json.getString("id");
        this.kind = OSTriggerKind.fromString(json.getString("kind"));
        this.property = json.optString("property", null);
        this.operatorType = OSTriggerOperator.fromString(json.getString("operator"));
        this.value = json.opt("value");
    }

    public JSONObject toJSONObject() {
        JSONObject json = new JSONObject();

        try {
            json.put("id", this.triggerId);
            json.put("kind", this.kind);
            json.put("property", this.property);
            json.put("operator", this.operatorType.toString());
            json.put("value", this.value);

        } catch (JSONException exception) {
            exception.printStackTrace();
        }

        return json;
    }

    @Override
    public String toString() {
        return "OSTrigger{" +
                "triggerId='" + triggerId + '\'' +
                ", kind=" + kind +
                ", property='" + property + '\'' +
                ", operatorType=" + operatorType +
                ", value=" + value +
                '}';
    }
}
