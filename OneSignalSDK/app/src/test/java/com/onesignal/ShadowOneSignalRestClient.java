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
