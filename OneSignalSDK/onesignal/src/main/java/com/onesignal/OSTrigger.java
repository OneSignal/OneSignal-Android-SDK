package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public class OSTrigger {

    /**
     * An enumeration of the operators triggers can use
     */
    public enum OSTriggerOperatorType {
        GREATER_THAN(">"),
        LESS_THAN("<"),
        EQUAL_TO("=="),
        NOT_EQUAL_TO("!="),
        LESS_THAN_OR_EQUAL_TO("<="),
        GREATER_THAN_OR_EQUAL_TO(">="),
        EXISTS("exists"),
        NOT_EXISTS("not_exists"),
        CONTAINS("contains");

        private String text;

        OSTriggerOperatorType(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return this.text;
        }

        public static OSTriggerOperatorType fromString(String text) {
            for (OSTriggerOperatorType type : OSTriggerOperatorType.values()) {
                if (type.text.equalsIgnoreCase(text)) {
                    return type;
                }
            }

            return null;
        }
    }

    /**
     * The property that this trigger operates on, such as 'game_score'
     */
    @NonNull
    public String property;


    /**
     * The type of operator used to perform the logical equivalence/comparison on,
     * such as > or <=
     */
    @NonNull
    public OSTriggerOperatorType operatorType;

    /**
     * Most comparison-based operators have a value to allow for triggers
     * such as game_score > 30
     */
    @Nullable
    public Object value;

    public OSTrigger() {  }

    public OSTrigger(JSONObject json) throws JSONException {
        this.property = json.getString("property");
        this.operatorType = OSTriggerOperatorType.fromString(json.getString("operator"));
        this.value = json.opt("value");
    }

    public JSONObject toJSONObject() {
        JSONObject json = new JSONObject();

        try {
            json.put("property", this.property);

            json.put("operator", this.operatorType.toString());

            json.putOpt("value", this.value);

        } catch (JSONException exception) {
            exception.printStackTrace();
        }


        return json;
    }
}
