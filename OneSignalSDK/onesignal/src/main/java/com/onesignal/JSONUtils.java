package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


class JSONUtils {

    /**
     * Returns a JSONObject of the differences between cur and changedTo.
     * If baseOutput is added changes will be applied to this JSONObject.
     * includeFields will always be added to the returned JSONObject if they are in cur.
     */
    static JSONObject generateJsonDiff(JSONObject cur, JSONObject changedTo, JSONObject baseOutput, Set<String> includeFields) {
        if (cur == null)
            return null;
        if (changedTo == null)
            return baseOutput;

        Iterator<String> keys = changedTo.keys();
        String key;
        Object value;

        JSONObject output;
        if (baseOutput != null)
            output = baseOutput;
        else
            output = new JSONObject();

        while (keys.hasNext()) {
            try {
                key = keys.next();
                value = changedTo.get(key);

                if (cur.has(key)) {
                    if (value instanceof JSONObject) {
                        JSONObject curValue = cur.getJSONObject(key);
                        JSONObject outValue = null;
                        if (baseOutput != null && baseOutput.has(key))
                            outValue = baseOutput.getJSONObject(key);
                        JSONObject returnedJson = generateJsonDiff(curValue, (JSONObject) value, outValue, includeFields);
                        String returnedJsonStr = returnedJson.toString();
                        if (!returnedJsonStr.equals("{}"))
                            output.put(key, new JSONObject(returnedJsonStr));
                    }
                    else if (value instanceof JSONArray)
                        handleJsonArray(key, (JSONArray) value, cur.getJSONArray(key), output);
                    else if (includeFields != null && includeFields.contains(key))
                        output.put(key, value);
                    else {
                        Object curValue = cur.get(key);
                        if (!value.equals(curValue)) {
                            // Work around for JSON serializer turning doubles/floats into ints since it drops ending 0's
                            if (curValue instanceof Integer && !"".equals(value)) {
                                if ( ((Number)curValue).doubleValue() != ((Number)value).doubleValue())
                                    output.put(key, value);
                            }
                            else
                                output.put(key, value);
                        }
                    }
                }
                else {
                    if (value instanceof JSONObject)
                        output.put(key, new JSONObject(value.toString()));
                    else if (value instanceof JSONArray)
                        handleJsonArray(key, (JSONArray) value, null, output);
                    else
                        output.put(key, value);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return output;
    }

    private static void handleJsonArray(String key, JSONArray newArray, JSONArray curArray, JSONObject output) throws JSONException {
        if (key.endsWith("_a") || key.endsWith("_d")) {
            output.put(key, newArray);
            return;
        }

        String arrayStr = toStringNE(newArray);

        JSONArray newOutArray = new JSONArray();
        JSONArray remOutArray = new JSONArray();
        String curArrayStr = curArray == null ? null : toStringNE(curArray);

        for (int i = 0; i < newArray.length(); i++) {
            String arrayValue = (String)newArray.get(i);
            if (curArray == null || !curArrayStr.contains(arrayValue))
                newOutArray.put(arrayValue);
        }

        if (curArray != null) {
            for (int i = 0; i < curArray.length(); i++) {
                String arrayValue = curArray.getString(i);
                if (!arrayStr.contains(arrayValue))
                    remOutArray.put(arrayValue);
            }
        }

        if (!newOutArray.toString().equals("[]"))
            output.put(key + "_a", newOutArray);
        if (!remOutArray.toString().equals("[]"))
            output.put(key + "_d", remOutArray);
    }

    static String toStringNE(JSONArray jsonArray) {
        String strArray = "[";

        try {
            for (int i = 0; i < jsonArray.length(); i++)
                strArray += "\"" + jsonArray.getString(i) + "\"";
        } catch (JSONException ignored) {}

        return strArray + "]";
    }

    static JSONObject getJSONObjectWithoutBlankValues(ImmutableJSONObject jsonObject, String getKey) {
        if (!jsonObject.has(getKey))
            return null;

        JSONObject toReturn = new JSONObject();

        JSONObject keyValues = jsonObject.optJSONObject(getKey);

        Iterator<String> keys = keyValues.keys();
        String key;
        Object value;

        while (keys.hasNext()) {
            key = keys.next();
            try {
                value = keyValues.get(key);
                if (!"".equals(value))
                    toReturn.put(key, value);
            } catch (JSONException ignored) {}
        }

        return toReturn;
    }

    // Converts a JSONObject into a Map, returns null if a null value is passed in.
    static @Nullable Map<String, Object> jsonObjectToMap(@Nullable JSONObject json) throws JSONException {
        if (json == null || json == JSONObject.NULL)
            return null;
        return jsonObjectToMapNonNull(json);
    }

    /**
     * Converts a JSONObject into a Map, same as above however does NOT accept null values
     */
    private static @NonNull Map<String, Object> jsonObjectToMapNonNull(@NonNull JSONObject object) throws JSONException {
        Map<String, Object> map = new HashMap<>();
        Iterator<String> keysItr = object.keys();
        while(keysItr.hasNext()) {
            String key = keysItr.next();
            Object value = object.get(key);
            map.put(key, convertNestedJSONType(value));
        }
        return map;
    }

    /**
     * Converts a JSONArray into a List, returns null if a null value is passed in.
     */
    static @Nullable List<Object> jsonArrayToList(@Nullable JSONArray array) throws JSONException {
        if (array == null)
            return null;
        return jsonArrayToListNonNull(array);
    }

    // Converts a JSONArray into a List, same as above however does NOT accept null values
    private static @NonNull List<Object> jsonArrayToListNonNull(@NonNull JSONArray array) throws JSONException {
        List<Object> list = new ArrayList<>();
        for(int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            list.add(convertNestedJSONType(value));
        }
        return list;
    }

    /**
     * Digs into any nested JSONObject or JSONArray to convert them to a List or Map
     * If object is another type is is returned back.
     */
    private static @NonNull Object convertNestedJSONType(@NonNull Object value) throws JSONException {
        if (value instanceof JSONObject)
            return jsonObjectToMapNonNull((JSONObject)value);
        if (value instanceof JSONArray)
            return jsonArrayToListNonNull((JSONArray)value);
        return value;
    }

    /**
     * Compare two JSONArrays too determine if they are equal or not
     */
    static boolean compareJSONArrays(JSONArray jsonArray1, JSONArray jsonArray2) {
        // If both JSONArrays are null, they are equal
        if (jsonArray1 == null && jsonArray2 == null)
            return true;

        // If one JSONArray is null but not the other, they are not equal
        if (jsonArray1 == null || jsonArray2 == null)
            return false;

        // If one JSONArray is a different size then the other, they are not equal
        if (jsonArray1.length() != jsonArray2.length())
            return false;

        try {
            L1 : for (int i = 0; i < jsonArray1.length(); i++) {
                for (int j = 0; j < jsonArray2.length(); j++) {
                    Object obj1 = normalizeType(jsonArray1.get(i));
                    Object obj2 = normalizeType(jsonArray2.get(j));
                    // Make sure jsonArray1 current item exists somewhere inside jsonArray2
                    // If item found continue looping
                    if (obj1.equals(obj2))
                        continue L1;
                }

                // Could not find current item from jsonArray1 inside jsonArray2, so they are not equal
                return false;
            }
        } catch (JSONException e) {
            e.printStackTrace();

            // Exception thrown, return false
            return false;
        }

        // JSONArrays are equal
        return true;
    }

    // Converts Java types that are equivalent in the JSON format to the same types.
    // This allows for assertEquals on two values from JSONObject.get to test values as long as it
    //   returns in the same JSON output.
    public static Object normalizeType(Object object) {
        Class clazz = object.getClass();

        if (clazz.equals(Integer.class))
            return Long.valueOf((Integer)object);
        if (clazz.equals(Float.class))
            return Double.valueOf((Float)object);

        return object;
    }

}
