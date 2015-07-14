/**
 * Modified MIT License
 *
 * Copyright 2015 OneSignal
 *
 * Portions Copyright 2013 Google Inc.
 * This file includes portions from the Google GcmClient demo project
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.onesignal;

import android.content.Context;
import android.util.Log;

import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.ResponseHandlerInterface;

import org.json.JSONException;
import org.json.JSONObject;
import org.robolectric.annotation.Implements;

import java.io.UnsupportedEncodingException;

@Implements(OneSignalRestClient.class)
public class ShadowOneSignalRestClient {

    public static JSONObject lastPost;
    public static Thread testThread;
    public static boolean failNext;

    public static final String testUserId = "a2f7f967-e8cc-11e4-bed1-118f05be4511";

    static void postSync(Context context, String url, JSONObject jsonBody, ResponseHandlerInterface responseHandler) throws UnsupportedEncodingException {
        Log.i("SHADOW_postSync", "url: " + url);
        lastPost = jsonBody;

        if (failNext) {
            ((JsonHttpResponseHandler)responseHandler).onFailure(400, null, new Exception(),new JSONObject());
            testThread.interrupt();
            return;
        }

        String retJson = null;
        if (url.contains("on_session"))
            retJson = "{}";
        else
            retJson = "{\"id\": \"" + testUserId + "\"}";

        try {
            ((JsonHttpResponseHandler)responseHandler).onSuccess(200, null, new JSONObject(retJson));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        testThread.interrupt();
    }

    static void putSync(Context context, String url, JSONObject jsonBody, ResponseHandlerInterface responseHandler) throws UnsupportedEncodingException {
        Log.i("SHADOW_putSync", "url: " + url);
        lastPost = jsonBody;

        try {
            ((JsonHttpResponseHandler)responseHandler).onSuccess(200, null, new JSONObject("{\"id\": \"" + testUserId + "\"}"));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        testThread.interrupt();
    }

    static void put(final Context context, final String url, JSONObject jsonBody, final ResponseHandlerInterface responseHandler) throws UnsupportedEncodingException {
        Log.i("SHADOW_put", "url: " + url);

        lastPost = jsonBody;

        try {
            ((JsonHttpResponseHandler)responseHandler).onSuccess(200, null, new JSONObject("{}"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
