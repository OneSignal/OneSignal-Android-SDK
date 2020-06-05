package com.test.onesignal;

import android.support.annotation.NonNull;

import com.onesignal.OneSignalPackagePrivateHelper.UserState;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowOneSignalRestClient.REST_METHOD;
import com.onesignal.ShadowOneSignalRestClient.Request;

import org.hamcrest.core.AnyOf;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import static com.onesignal.OneSignalPackagePrivateHelper.UserState.PUSH_STATUS_SUBSCRIBED;
import static com.test.onesignal.RestClientValidator.GET_REMOTE_PARAMS_ENDPOINT;
import static com.test.onesignal.TypeAsserts.assertIsUUID;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

class RestClientAsserts {

   private static final AnyOf<Integer> ANY_OF_VALID_DEVICE_TYPES = anyOf(
      is(UserState.DEVICE_TYPE_ANDROID),
      is(UserState.DEVICE_TYPE_FIREOS),
      is(UserState.DEVICE_TYPE_EMAIL),
      is(UserState.DEVICE_TYPE_HUAWEI)
   );

   private static final AnyOf<Integer> ANY_OF_PUSH_DEVICE_TYPES = anyOf(
      is(UserState.DEVICE_TYPE_ANDROID),
      is(UserState.DEVICE_TYPE_FIREOS),
      is(UserState.DEVICE_TYPE_HUAWEI)
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

   static void assertAndroidPlayerCreateAtIndex(int index) throws JSONException {
      Request request = ShadowOneSignalRestClient.requests.get(index);

      assertPlayerCreateMethodAndUrl(request);
      assertDeviceTypeIsAndroid(request.payload);
   }

   static void assertAmazonPlayerCreateAtIndex(int index) throws JSONException {
      Request request = ShadowOneSignalRestClient.requests.get(index);

      assertPlayerCreateMethodAndUrl(request);
      assertDeviceTypeIsAmazon(request.payload);
   }

   static void assertHuaweiPlayerCreateAtIndex(int index) throws JSONException {
      Request request = ShadowOneSignalRestClient.requests.get(index);

      assertPlayerCreateMethodAndUrl(request);
      assertDeviceTypeIsHuawei(request.payload);
   }

   static void assertPlayerCreateSubscribedAtIndex(int index) throws JSONException {
      Request request = ShadowOneSignalRestClient.requests.get(index);

      assertPlayerCreateMethodAndUrl(request);

      boolean subscribed = !request.payload.has("notification_types");
      assertTrue(subscribed);
      assertNotNull(request.payload.optString("identifier", null));
   }

   static void assertPlayerCreateNotSubscribedAtIndex(int index) throws JSONException {
      Request request = ShadowOneSignalRestClient.requests.get(index);

      assertPlayerCreateMethodAndUrl(request);

      boolean unsubscribed = request.payload.getInt("notification_types") < PUSH_STATUS_SUBSCRIBED;
      assertTrue(unsubscribed);
      assertNull(request.payload.optString("identifier", null));
   }

   static void assertPlayerCreateWithNotificationTypesAtIndex(int notification_types, int index) throws JSONException {
      Request request = ShadowOneSignalRestClient.requests.get(index);
      assertPlayerCreateMethodAndUrl(request);
      assertEquals(notification_types, request.payload.getInt("notification_types"));
   }

   static void assertOnSessionAtIndex(int index) {
      Request request = ShadowOneSignalRestClient.requests.get(index);

      assertOnSessionUrl(request.url);
   }

   static void assertOnFocusAtIndex(int index, int focusTimeSec) throws JSONException {
      assertOnFocusAtIndex(index, new JSONObject().put("active_time", focusTimeSec));
   }

   static void assertOnFocusAtIndex(int index, @NonNull JSONObject containsPayload) throws JSONException {
      Request request = ShadowOneSignalRestClient.requests.get(index);

      assertEquals(REST_METHOD.POST, request.method);
      assertOnFocusUrl(request.url);
      JsonAsserts.containsSubset(request.payload, containsPayload);
   }

   static void assertOnFocusAtIndexForPlayerId(int index, @NonNull String id) {
      Request request = ShadowOneSignalRestClient.requests.get(index);

      assertEquals(REST_METHOD.POST, request.method);
      assertOnFocusUrlWithPlayerId(request.url, id);
   }

   static void assertOnFocusAtIndexDoesNotHaveKeys(int index, @NonNull List<String> omitKeys) {
      Request request = ShadowOneSignalRestClient.requests.get(index);

      assertEquals(REST_METHOD.POST, request.method);
      assertOnFocusUrl(request.url);
      JsonAsserts.doesNotContainKeys(request.payload, omitKeys);
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

   private static void assertDeviceTypeIsAndroid(@NonNull JSONObject payload) throws JSONException {
      assertEquals(UserState.DEVICE_TYPE_ANDROID, payload.getInt("device_type"));
   }

   private static void assertDeviceTypeIsAmazon(@NonNull JSONObject payload) throws JSONException {
      assertEquals(UserState.DEVICE_TYPE_FIREOS, payload.getInt("device_type"));
   }

   private static void assertDeviceTypeIsHuawei(@NonNull JSONObject payload) throws JSONException {
      assertEquals(UserState.DEVICE_TYPE_HUAWEI, payload.getInt("device_type"));
   }

   static void assertReportReceivedAtIndex(int index, @NonNull String notificationId, @NonNull JSONObject payload) {
      Request request = ShadowOneSignalRestClient.requests.get(index);
      assertEquals(REST_METHOD.PUT, request.method);
      assertRemoteParamsUrlReportReceived(request.url, notificationId);
      assertEquals(payload.toString(), request.payload.toString());
   }

   static void assertRemoteParamsUrlReportReceived(@NonNull String url, @NonNull String notificationId) {
      String[] parts = url.split("/");
      assertEquals("notifications", parts[0]);
      assertEquals(notificationId, parts[1]);
      assertEquals("report_received", parts[2]);
   }

   static void assertRemoteParamsAtIndex(int index) {
      Request request = ShadowOneSignalRestClient.requests.get(index);

      assertEquals(REST_METHOD.GET, request.method);
      assertRemoteParamsUrl(request.url);
   }

   static void assertMeasureAtIndex(int index, @NonNull String outcomeName) throws JSONException {
      assertMeasureAtIndex("measure", index, new JSONObject()
              .put("id", outcomeName)
      );
   }

   static void assertMeasureAtIndex(int index, @NonNull boolean isDirect, @NonNull String outcomeName, @NonNull JSONArray notificationIds) throws JSONException {
      assertMeasureAtIndex("measure", index, new JSONObject()
              .put("direct", isDirect)
              .put("id", outcomeName)
              .put("notification_ids", notificationIds)
      );
   }

   static void assertMeasureOnV2AtIndex(int index, @NonNull String outcomeName,
                                        JSONArray directIAMs, JSONArray directNotifications,
                                        JSONArray indirectIAMs, JSONArray indirectNotifications) throws JSONException {
      JSONObject sources = new JSONObject();
      boolean direct = false;
      boolean indirect = false;
      if (directIAMs != null || directNotifications != null) {
         direct = true;
         JSONObject directBody = new JSONObject();
         if (directNotifications != null)
            directBody.put("notification_ids", directNotifications);
         if (directIAMs != null)
            directBody.put("in_app_message_ids", directIAMs);
         sources.put("direct", directBody);
      }

      if (indirectIAMs != null || indirectNotifications != null) {
         indirect = true;
         JSONObject indirectBody = new JSONObject();
         if (indirectNotifications != null)
            indirectBody.put("notification_ids", indirectNotifications);
         if (indirectIAMs != null)
            indirectBody.put("in_app_message_ids", indirectIAMs);
         sources.put("indirect", indirectBody);
      }

      assertMeasureAtIndex("measure_sources", index, new JSONObject()
              .put("id", outcomeName)
              .put("sources", sources)
      );

      Request request = ShadowOneSignalRestClient.requests.get(index);
      if (!direct)
         assertFalse(request.payload.getJSONObject("sources").has("direct"));
      if (!indirect)
         assertFalse(request.payload.getJSONObject("sources").has("indirect"));

      assertFalse(request.payload.has("weight"));
   }

   private static void assertMeasureAtIndex(String measureKey, int index, JSONObject containsPayload) throws JSONException {
      Request request = ShadowOneSignalRestClient.requests.get(index);

      assertEquals(REST_METHOD.POST, request.method);
      assertMeasureUrl(measureKey, request.url);
      JsonAsserts.containsSubset(request.payload, containsPayload);
   }

   static void assertOnFocusUrlWithPlayerId(@NonNull String url, @NonNull String id) {
      assertOnFocusUrl(url);
      assertEquals(id, url.split("/")[1]);
   }

   static void assertOnSessionUrl(String url) {
      String[] parts = url.split("/");
      assertEquals("players", parts[0]);
      assertIsUUID(parts[1]);
      assertEquals("on_session", parts[2]);
   }

   static void assertOnFocusUrl(@NonNull String url) {
      String[] parts = url.split("/");
      assertEquals("players", parts[0]);
      assertIsUUID(parts[1]);
      assertEquals("on_focus", parts[2]);
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

   private static void assertMeasureUrl(String measureKey, String url) {
      String[] parts = url.split("/");
      assertEquals("outcomes", parts[0]);
      assertEquals(measureKey, parts[1]);
   }

   static void assertRestCalls(int expected) {
      assertEquals(expected, ShadowOneSignalRestClient.networkCallCount);
   }

   static void assertHasAppId(@NonNull Request request) {
      if (request.method == REST_METHOD.GET) {
         assertAppIdInUrl(request.url);
         return;
      }

      assertIsUUID(request.payload.optString("app_id"));
   }

   public static void assertNotificationOpenAtIndex(int index, int deviceType) throws JSONException {
      Request request = ShadowOneSignalRestClient.requests.get(index);

      assertEquals(REST_METHOD.PUT, request.method);

      String[] parts = request.url.split("/");
      assertEquals("notifications", parts[0]);
      assertIsUUID(parts[1]);

      assertHasAppId(request);

      assertEquals(deviceType, request.payload.getInt("device_type"));
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
