/**
 * Modified MIT License
 * <p>
 * Copyright 2020 OneSignal
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.onesignal;

import org.json.JSONException;
import org.json.JSONObject;

class ImmutableJSONObject {

    private final JSONObject jsonObject;

    public ImmutableJSONObject() {
        this.jsonObject = new JSONObject();
    }

    public ImmutableJSONObject(JSONObject jsonObject) {
        this.jsonObject = jsonObject;
    }

    public long getLong(String name) throws JSONException {
        return jsonObject.getLong(name);
    }

    public boolean has(String name) {
        return jsonObject.has(name);
    }

    public Object opt(String name) {
        return jsonObject.opt(name);
    }

    public String optString(String name) {
        return jsonObject.optString(name);
    }

    public String optString(String name, String fallback) {
        return jsonObject.optString(name, fallback);
    }

    public boolean optBoolean(String name) {
        return jsonObject.optBoolean(name);
    }

    public boolean optBoolean(String name, boolean fallback) {
        return jsonObject.optBoolean(name, fallback);
    }

    public long optLong(String name) {
        return jsonObject.optLong(name);
    }

    public int optInt(String name) {
        return jsonObject.optInt(name);
    }

    public int optInt(String name, int fallback) {
        return jsonObject.optInt(name, fallback);
    }

    public JSONObject optJSONObject(String name) {
        return jsonObject.optJSONObject(name);
    }

    @Override
    public String toString() {
        return "ImmutableJSONObject{" +
                "jsonObject=" + jsonObject +
                '}';
    }
}
