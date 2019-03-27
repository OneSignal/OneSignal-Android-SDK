package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

class OSInAppMessage {

    /**
     * The unique identifier for this in-app message
     */
    @NonNull
    public String messageId;

    /**
     * Allows in-app messages to use multiple language variants, or to have variations between
     * different device types (ie. a different image for phones vs. tablets, etc.).
     *
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

    public double maxDisplayTime;

    @Nullable
    public ArrayList<OSInAppMessageAction> actions;

    OSInAppMessage(JSONObject json) throws JSONException {

        // initialize simple root properties
        this.messageId = json.getString("id");
        this.maxDisplayTime = json.optDouble("max_display_time");
        this.variants = parseVariants(json.getJSONObject("variants"));
        this.triggers = parseTriggerJson(json.getJSONArray("triggers"));

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

    private static HashMap<String, HashMap<String, String>> parseVariants(JSONObject json) throws JSONException {
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

    ArrayList<ArrayList<OSTrigger>> parseTriggerJson(JSONArray triggersJson) throws JSONException {
        // initialize triggers
        ArrayList<ArrayList<OSTrigger>> parsedTriggers = new ArrayList<>();

        for (int i = 0; i < triggersJson.length(); i++) {
            JSONArray ands = triggersJson.getJSONArray(i);
            ArrayList<OSTrigger> converted = new ArrayList<>();
            for (int j = 0; j < ands.length(); j++)
                converted.add(new OSTrigger(ands.getJSONObject(j)));

            parsedTriggers.add(converted);
        }

        return parsedTriggers;
    }

    JSONObject toJSONObject() {
        JSONObject json = new JSONObject();

        try {
            json.put("id", this.messageId);

            JSONObject variants = new JSONObject();
            for (String key : this.variants.keySet()) {
                HashMap<String, String> variant = this.variants.get(key);
                JSONObject converted = new JSONObject();

                for (String variantKey : variant.keySet())
                    converted.put(variantKey, variant.get(variantKey));

                variants.put(key, converted);
            }

            json.put("variants", variants);
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
