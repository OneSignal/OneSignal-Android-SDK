package com.onesignal;

import android.support.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class OSInAppMessageTag {

    //TODO when backend is ready check if key match
    private static final String ADD_TAGS = "adds";
    //TODO when backend is ready check if key match
    private static final String REMOVE_TAGS = "removes";

    private JSONObject tagsToAdd;
    private JSONArray tagsToRemove;

    OSInAppMessageTag(@NonNull JSONObject json) throws JSONException {
        tagsToAdd = json.has(ADD_TAGS) ? json.getJSONObject(ADD_TAGS) : null;
        tagsToRemove = json.has(REMOVE_TAGS) ? json.getJSONArray(REMOVE_TAGS) : null;
    }

    public JSONObject toJSONObject() {
        JSONObject mainObj = new JSONObject();
        try {
            if (tagsToAdd != null)
                mainObj.put(ADD_TAGS, tagsToAdd);
            if (tagsToRemove != null)
                mainObj.put(REMOVE_TAGS, tagsToRemove);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return mainObj;
    }

    public JSONObject getTagsToAdd() {
        return tagsToAdd;
    }

    public void setTagsToAdd(JSONObject tagsToAdd) {
        this.tagsToAdd = tagsToAdd;
    }

    public JSONArray getTagsToRemove() {
        return tagsToRemove;
    }

    public void setTagsToRemove(JSONArray tagsToRemove) {
        this.tagsToRemove = tagsToRemove;
    }

    @Override
    public String toString() {
        return "OSInAppMessageTag{" +
                "adds=" + tagsToAdd +
                ", removes=" + tagsToRemove +
                '}';
    }
}