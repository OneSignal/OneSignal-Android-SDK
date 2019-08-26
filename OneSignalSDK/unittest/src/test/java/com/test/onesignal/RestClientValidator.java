package com.test.onesignal;

import android.support.annotation.NonNull;

import com.onesignal.ShadowOneSignalRestClient.Request;

import org.json.JSONException;

import static com.test.onesignal.RestClientAsserts.assertHasAppId;
import static com.test.onesignal.RestClientAsserts.assertPlayerCreateAny;
import static com.test.onesignal.RestClientAsserts.assertRemoteParamsUrl;

// Validator runs on each mock REST API call on All tests to ensure the correct fields are sent
public class RestClientValidator {

   static final String GET_REMOTE_PARAMS_ENDPOINT = "android_params.js";

   public static void validateRequest(@NonNull Request request) throws JSONException {
      switch (request.method) {
         case GET:
            if (request.url.contains(GET_REMOTE_PARAMS_ENDPOINT))
               assertRemoteParamsUrl(request.url);
            break;
         case POST:
            if (request.url.endsWith("players"))
               assertPlayerCreateAny(request);
      }

      assertHasAppId(request);

      // TODO: Add rest of the REST API calls here
   }

}
