package com.test.onesignal;

import android.support.annotation.NonNull;

import com.onesignal.OneSignalPackagePrivateHelper.UserState;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowOneSignalRestClient.Request;
import com.onesignal.ShadowOneSignalRestClient.REST_METHOD;

import org.hamcrest.core.AnyOf;
import org.json.JSONException;
import org.json.JSONObject;

import static com.test.onesignal.RestClientValidator.GET_REMOTE_PARAMS_ENDPOINT;
import static com.test.onesignal.TypeAsserts.assertIsUUID;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

class RestClientAsserts {

   private static final AnyOf<Integer> ANY_OF_VALID_DEVICE_TYPES = anyOf(
      is(UserState.DEVICE_TYPE_ANDROID),
      is(UserState.DEVICE_TYPE_FIREOS),
      is(UserState.DEVICE_TYPE_EMAIL)
   );

   private static final AnyOf<Integer> ANY_OF_PUSH_DEVICE_TYPES = anyOf(
      is(UserState.DEVICE_TYPE_ANDROID),
      is(UserState.DEVICE_TYPE_FIREOS)
   );

   static void assertPlayerCreateAnyAtIndex(int index) throws JSONException {
      assertPlayerCreateAny(ShadowOneSignalRestClient.requests.get(index));
   }

   static void assertPlayerCreateAny(Request request) throws JSONException {
      assertPlayerCreateMethodAndUrl(request);
      assertValidDeviceType(request.payload);
   }

   static void assertPlayerCreatePush(Request request) throws JSONException {
      assertPlayerCreateMethodAndUrl(request);
      assertDeviceTypeIsPush(request.payload);
   }

   static void assertPlayerCreatePushAtIndex(int index) throws JSONException {
      assertPlayerCreatePush(ShadowOneSignalRestClient.requests.get(index));
   }

   private static void assertPlayerCreateMethodAndUrl(@NonNull Request request) {
      assertEquals(REST_METHOD.POST, request.method);
      assertEquals(request.url, "players");
   }

   private static void assertDeviceTypeIsPush(@NonNull JSONObject payload) throws JSONException {
      assertThat(payload.getInt("device_type"), ANY_OF_PUSH_DEVICE_TYPES);
   }

   private static void assertValidDeviceType(@NonNull JSONObject payload) throws JSONException {
      assertThat(payload.getInt("device_type"), ANY_OF_VALID_DEVICE_TYPES);
   }

   static void assertRemoteParamsAtIndex(int index) {
      Request request = ShadowOneSignalRestClient.requests.get(index);

      assertEquals(REST_METHOD.GET, request.method);
      assertRemoteParamsUrl(request.url);
   }

   // Assert that URL matches the format apps/{UUID}/android_params.js
   static void assertRemoteParamsUrl(@NonNull String url) {
      String[] parts = url.split("/");

      assertEquals("apps", parts[0]);
      assertIsUUID(parts[1]);

      String[] playerIdParts = parts[2].split("\\?player_id=");
      assertEquals(GET_REMOTE_PARAMS_ENDPOINT, playerIdParts[0]);
      if (playerIdParts.length == 2)
         assertIsUUID( playerIdParts[1]);
      else if (playerIdParts.length > 2)
         fail("Invalid format");

      assertEquals(3, parts.length);
   }

   static void assertRestCalls(int calls) {
      assertEquals(calls, ShadowOneSignalRestClient.networkCallCount);
   }

   static void assertHasAppId(@NonNull Request request) {
      if (request.method == REST_METHOD.GET) {
         assertAppIdInUrl(request.url);
         return;
      }

      assertIsUUID(request.payload.optString("app_id"));
   }

   private static void assertAppIdInUrl(@NonNull String url) {
      String[] appsPath = url.split("apps/");
      if (appsPath.length == 2) {
         String[] appIdPart = appsPath[1].split("/");
         assertIsUUID(appIdPart[0]);
      }
      else if (appsPath.length == 1) {
         // Check for "?app_id=" or "&app_id="
         String[] appsQueryParam = url.split("\\?app_id=|&app_id=");
         assertIsUUID(appsQueryParam[1]);
      }
      else
         fail("Invalid format");
   }
}
