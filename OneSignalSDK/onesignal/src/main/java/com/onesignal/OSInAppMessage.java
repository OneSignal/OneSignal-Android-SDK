package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.ArrayList;

class OSInAppMessage {

    /**
     * The unique identifier for this in-app message
     */
    @NonNull
    public String messageId;

    /**
     * The unique identifier for the in-app message webpage content (HTML)
     */
    @NonNull
    public String contentId;

    /**
     * An ar1ray of arrays of triggers. The outer array represents AND conditions,
     * while the inner array represents AND conditions.
     */
    @NonNull
    public ArrayList<ArrayList<OSTrigger>> triggers;


    @Nullable
    public double maxDisplayTime;

    @Nullable
    public ArrayList<OSInAppMessageAction> actions;

    public OSInAppMessage() { }

    public OSInAppMessage(JSONObject json) throws JSONException {

        // initialize simple root properties
        this.messageId = json.getString("id");
        this.contentId = json.getString("content_id");
        this.maxDisplayTime = json.optDouble("max_display_time");

        JSONArray ors = json.getJSONArray("triggers");

        this.parseTriggerJson(ors);

        if (json.has("actions")) {
            actions = new ArrayList<>();

            JSONArray jsonActions = json.getJSONArray("actions");

            for (int i = 0; i < jsonActions.length(); i++) {
                JSONObject actionJson = jsonActions.getJSONObject(i);

                OSInAppMessageAction action = new OSInAppMessageAction(actionJson);

                actions.add(action);
            }
        }
    }

    public void parseTriggerJson(JSONArray triggersJson) throws JSONException {
        // initialize triggers
        this.triggers = new ArrayList<>();

        for (int i = 0; i < triggersJson.length(); i++) {
            JSONArray ands = triggersJson.getJSONArray(i);

            ArrayList<OSTrigger> converted = new ArrayList();

            for (int j = 0; j < ands.length(); j++)
                converted.add(new OSTrigger(ands.getJSONObject(j)));

            this.triggers.add(converted);
        }
    }

    public JSONObject toJSONObject() {
        JSONObject json = new JSONObject();

        try {
            json.put("id", this.messageId);

            json.put("content_id", this.contentId);

            json.put("max_display_time", this.maxDisplayTime);

            JSONArray orConditions = new JSONArray();

            for (ArrayList<OSTrigger> andArray : this.triggers) {
                JSONArray andConditions = new JSONArray();

                for (OSTrigger trigger : andArray)
                    andConditions.put(trigger.toJSONObject());

                orConditions.put(andConditions);
            }

            json.put("triggers", orConditions);

        } catch (JSONException exception) {
            exception.printStackTrace();
        }

        return json;
    }
}
