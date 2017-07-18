/**
 * Modified MIT License
 *
 * Copyright 2017 OneSignal
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
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

class OneSignalStateSynchronizer {
   private static boolean nextSyncIsSession = false, waitingForSessionResponse = false;

   // currentUserState - Current known state of the user on OneSignal's server.
   // toSyncUserState  - Pending state that will be synced to the OneSignal server.
   //                    diff will be generated between currentUserState when a sync call is made to the server.
   private static UserState currentUserState, toSyncUserState;
   
   private static UserState getToSyncUserState() {
      synchronized (syncLock) {
         if (toSyncUserState == null)
            toSyncUserState = new OneSignalStateSynchronizer().new UserState("TOSYNC_STATE", true);
      }
      
      return toSyncUserState;
   }

   static HashMap<Integer, NetworkHandlerThread> networkHandlerThreads = new HashMap<>();
   private static final Object networkHandlerSyncLock = new Object() {};

   private static Context appContext;

   private static final String[] LOCATION_FIELDS = new String[] { "lat", "long", "loc_acc", "loc_type", "loc_bg", "ad_id"};
   private static final Set<String> LOCATION_FIELDS_SET = new HashSet<>(Arrays.asList(LOCATION_FIELDS));

   // Object to synchronize on to prevent concurrent modifications on syncValues and dependValues
   private static final Object syncLock = new Object() {};

   static private JSONObject generateJsonDiff(JSONObject cur, JSONObject changedTo, JSONObject baseOutput, Set<String> includeFields) {
      synchronized (syncLock) {
         return synchronizedGenerateJsonDiff(cur, changedTo, baseOutput, includeFields);
      }
   }

   static private JSONObject synchronizedGenerateJsonDiff(JSONObject cur, JSONObject changedTo, JSONObject baseOutput, Set<String> includeFields) {
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
                  JSONObject returnedJson = synchronizedGenerateJsonDiff(curValue, (JSONObject) value, outValue, includeFields);
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

   private static String toStringNE(JSONArray jsonArray) {
      String strArray = "[";

      try {
         for (int i = 0; i < jsonArray.length(); i++)
            strArray += "\"" + jsonArray.getString(i) + "\"";
      } catch (Throwable t) {}

      return strArray + "]";
   }

   private static JSONObject getTagsWithoutDeletedKeys(JSONObject jsonObject) {
      if (jsonObject.has("tags")) {
         JSONObject toReturn = new JSONObject();

         synchronized (syncLock) {
            JSONObject keyValues = jsonObject.optJSONObject("tags");

            Iterator<String> keys = keyValues.keys();
            String key;
            Object value;

            while (keys.hasNext()) {
               key = keys.next();
               try {
                  value = keyValues.get(key);
                  if (!"".equals(value))
                     toReturn.put(key, value);
               } catch (Throwable t) {}
            }
         
            return toReturn;
         }
      }

      return null;
   }
   
   static boolean stopAndPersist() {
      for (Map.Entry<Integer, OneSignalStateSynchronizer.NetworkHandlerThread> handlerThread : OneSignalStateSynchronizer.networkHandlerThreads.entrySet())
         handlerThread.getValue().stopScheduledRunnable();
      
      if (toSyncUserState != null) {
         boolean unSynced = currentUserState.generateJsonDiff(toSyncUserState, isSessionCall()) != null;
         toSyncUserState.persistState();
         return unSynced;
      }
      return false;
   }
   
   static void clearLocation() {
      getToSyncUserState().clearLocation();
      getToSyncUserState().persistState();
   }
   
   class UserState {
      
      private final int NOTIFICATION_TYPES_SUBSCRIBED = 1;
      private final int NOTIFICATION_TYPES_NO_PERMISSION = 0;
      private final int NOTIFICATION_TYPES_UNSUBSCRIBE = -2;

      private String persistKey;

      JSONObject dependValues, syncValues;

      private UserState(String inPersistKey, boolean load) {
         persistKey = inPersistKey;
         if (load)
            loadState();
         else {
            dependValues = new JSONObject();
            syncValues = new JSONObject();
         }
      }

      private UserState deepClone(String persistKey) {
         UserState clonedUserState = new UserState(persistKey, false);

         try {
            clonedUserState.dependValues = new JSONObject(dependValues.toString());
            clonedUserState.syncValues = new JSONObject(syncValues.toString());
         } catch (JSONException e) {
            e.printStackTrace();
         }

         return clonedUserState;
      }

      private void addDependFields() {
         try {
            syncValues.put("notification_types", getNotificationTypes());
         } catch (JSONException e) {}
      }

      private int getNotificationTypes() {
         int subscribableStatus = dependValues.optInt("subscribableStatus", 1);
         if (subscribableStatus < NOTIFICATION_TYPES_UNSUBSCRIBE)
            return subscribableStatus;
   
         boolean androidPermission = dependValues.optBoolean("androidPermission", true);
         if (!androidPermission)
            return NOTIFICATION_TYPES_NO_PERMISSION;
   
         boolean userSubscribePref = dependValues.optBoolean("userSubscribePref", true);
         if (!userSubscribePref)
            return NOTIFICATION_TYPES_UNSUBSCRIBE;
         
         return NOTIFICATION_TYPES_SUBSCRIBED;
      }

      private Set<String> getGroupChangeFields(UserState changedTo) {
         try {
            if (dependValues.optLong("loc_time_stamp") != changedTo.dependValues.getLong("loc_time_stamp")
                || syncValues.optDouble("lat") != changedTo.syncValues.getDouble("lat")
                || syncValues.optDouble("long") != changedTo.syncValues.getDouble("long")
                || syncValues.optDouble("loc_acc") != changedTo.syncValues.getDouble("loc_acc")
                || syncValues.optInt("loc_type ") != changedTo.syncValues.optInt("loc_type")) {
                  changedTo.syncValues.put("loc_bg", changedTo.dependValues.opt("loc_bg"));
               return LOCATION_FIELDS_SET;
            }
         } catch (Throwable t) {}

         return null;
      }
      
      void setLocation(LocationGMS.LocationPoint point) {
         try {
            syncValues.put("lat", point.lat);
            syncValues.put("long",point.log);
            syncValues.put("loc_acc", point.accuracy);
            syncValues.put("loc_type", point.type);
            dependValues.put("loc_bg", point.bg);
            dependValues.put("loc_time_stamp", point.timeStamp);
         } catch (JSONException e) {
            e.printStackTrace();
         }
      }
      
      void clearLocation() {
         try {
            syncValues.put("lat", null);
            syncValues.put("long", null);
            syncValues.put("loc_acc", null);
            syncValues.put("loc_type", null);
            syncValues.put("loc_bg", null);
            dependValues.put("loc_bg", null);
            dependValues.put("loc_time_stamp", null);
         } catch (JSONException e) {
            e.printStackTrace();
         }
      }

      private JSONObject generateJsonDiff(UserState newState, boolean isSessionCall) {
         addDependFields(); newState.addDependFields();
         Set<String> includeFields = getGroupChangeFields(newState);
         JSONObject sendJson = OneSignalStateSynchronizer.generateJsonDiff(syncValues, newState.syncValues, null, includeFields);

         if (!isSessionCall && sendJson.toString().equals("{}"))
            return null;

         try {
            // This makes sure app_id is in all our REST calls.
            if (!sendJson.has("app_id"))
               sendJson.put("app_id", syncValues.optString("app_id"));
         } catch (JSONException e) {
            e.printStackTrace();
         }

         return sendJson;
      }

      void set(String key, Object value) {
         try {
            syncValues.put(key, value);
         } catch (JSONException e) {
            e.printStackTrace();
         }
      }

      void setState(String key, Object value) {
         try {
            dependValues.put(key, value);
         } catch (JSONException e) {
            e.printStackTrace();
         }
      }

      private void loadState() {
         final SharedPreferences prefs = OneSignal.getGcmPreferences(appContext);

         String dependValuesStr = prefs.getString("ONESIGNAL_USERSTATE_DEPENDVALYES_" + persistKey, null);
         // null if first run of a 2.0+ version.
         if (dependValuesStr == null) {
            dependValues = new JSONObject();
            try {
               int subscribableStatus;
               boolean userSubscribePref = true;
               // Convert 1.X SDK settings to 2.0+.
               if (persistKey.equals("CURRENT_STATE"))
                  subscribableStatus = prefs.getInt("ONESIGNAL_SUBSCRIPTION", 1);
               else
                  subscribableStatus = prefs.getInt("ONESIGNAL_SYNCED_SUBSCRIPTION", 1);

               if (subscribableStatus == NOTIFICATION_TYPES_UNSUBSCRIBE) {
                  subscribableStatus = 1;
                  userSubscribePref = false;
               }

               dependValues.put("subscribableStatus", subscribableStatus);
               dependValues.put("userSubscribePref", userSubscribePref);
            } catch (JSONException e) {}
         }
         else {
            try {
               dependValues = new JSONObject(dependValuesStr);
            } catch (JSONException e) {
               e.printStackTrace();
            }
         }

         String syncValuesStr = prefs.getString("ONESIGNAL_USERSTATE_SYNCVALYES_" + persistKey, null);
         try {
            if (syncValuesStr == null) {
               syncValues = new JSONObject();
               syncValues.put("identifier", prefs.getString("GT_REGISTRATION_ID", null));
            }
            else
               syncValues = new JSONObject(syncValuesStr);
         } catch (JSONException e) {
            e.printStackTrace();
         }
      }

      private void persistState() {
         synchronized(syncLock) {
            modifySyncValuesJsonArray("pkgs");

            final SharedPreferences prefs = OneSignal.getGcmPreferences(appContext);
            SharedPreferences.Editor editor = prefs.edit();

            editor.putString("ONESIGNAL_USERSTATE_SYNCVALYES_" + persistKey, syncValues.toString());
            editor.putString("ONESIGNAL_USERSTATE_DEPENDVALYES_" + persistKey, dependValues.toString());
            editor.commit();
         }
      }

      private void modifySyncValuesJsonArray(String baseKey) {
         if (!syncValues.has(baseKey + "_d") && syncValues.has(baseKey + "_d"))
            return;

         try {
            JSONArray orgArray = syncValues.has(baseKey) ? syncValues.getJSONArray(baseKey) : new JSONArray();
            JSONArray tempArray = new JSONArray();

            if (syncValues.has(baseKey + "_d")) {
               String remArrayStr = toStringNE(syncValues.getJSONArray(baseKey + "_d"));
               for (int i = 0; i < orgArray.length(); i++)
                  if (!remArrayStr.contains(orgArray.getString(i)))
                     tempArray.put(orgArray.get(i));
            }
            else
               tempArray = orgArray;

            if (syncValues.has(baseKey + "_a")) {
               JSONArray newArray = syncValues.getJSONArray(baseKey + "_a");
               for (int i = 0; i < newArray.length(); i++)
                  tempArray.put(newArray.get(i));
            }

            syncValues.put(baseKey, tempArray);
            syncValues.remove(baseKey + "_a");
            syncValues.remove(baseKey + "_d");
         } catch (Throwable t) {}
      }

      private void persistStateAfterSync(JSONObject inDependValues, JSONObject inSyncValues) {
         if (inDependValues != null)
            OneSignalStateSynchronizer.generateJsonDiff(dependValues, inDependValues, dependValues, null);

         if (inSyncValues != null) {
            OneSignalStateSynchronizer.generateJsonDiff(syncValues, inSyncValues, syncValues, null);
            mergeTags(inSyncValues, null);
         }

         if (inDependValues != null || inSyncValues != null)
            persistState();
      }

      void mergeTags(JSONObject inSyncValues, JSONObject omitKeys) {
         synchronized (syncLock) {
            if (inSyncValues.has("tags")) {
               JSONObject newTags;
               if (syncValues.has("tags")) {
                  try {
                     newTags = new JSONObject(syncValues.optString("tags"));
                  } catch (JSONException e) {
                     newTags = new JSONObject();
                  }
               }
               else
                  newTags = new JSONObject();

               JSONObject curTags = inSyncValues.optJSONObject("tags");
               Iterator<String> keys = curTags.keys();
               String key;

               try {
                  while (keys.hasNext()) {
                     key = keys.next();
                     if ("".equals(curTags.optString(key)))
                        newTags.remove(key);
                     else if (omitKeys == null || !omitKeys.has(key))
                        newTags.put(key, curTags.optString(key));
                  }

                  if (newTags.toString().equals("{}"))
                     syncValues.remove("tags");
                  else
                     syncValues.put("tags", newTags);
               } catch (Throwable t) {}
            }
         }
      }
   }

   static class NetworkHandlerThread extends HandlerThread {
      private static final int NETWORK_HANDLER_USERSTATE = 0;

      int mType;

      Handler mHandler = null;

      static final int MAX_RETRIES = 3;
      int currentRetry;

      NetworkHandlerThread(int type) {
         super("OSH_NetworkHandlerThread");
         mType = type;
         start();
         mHandler = new Handler(getLooper());
      }
   
      void runNewJob() {
         currentRetry = 0;
         mHandler.removeCallbacksAndMessages(null);
         mHandler.postDelayed(getNewRunnable(), 5000);
      }

      private Runnable getNewRunnable() {
         switch (mType) {
            case NETWORK_HANDLER_USERSTATE:
               return new Runnable() {
                  @Override
                  public void run() {
                     syncUserState(false);
                  }
               };
         }

         return null;
      }

      void stopScheduledRunnable() {
         mHandler.removeCallbacksAndMessages(null);
      }

      void doRetry() {
         if (currentRetry < MAX_RETRIES && !mHandler.hasMessages(0)) {
            currentRetry++;
            mHandler.postDelayed(getNewRunnable(), currentRetry * 15000);
         }
      }
   }

   static void initUserState(Context context) {
      appContext = context;

      synchronized (syncLock) {
         if (currentUserState == null)
            currentUserState = new OneSignalStateSynchronizer().new UserState("CURRENT_STATE", true);

         if (toSyncUserState == null)
            toSyncUserState = new OneSignalStateSynchronizer().new UserState("TOSYNC_STATE", true);
      }
   }

   static UserState getNewUserState() {
      return new OneSignalStateSynchronizer().new UserState("nonPersist", false);
   }
   
   private static boolean isSessionCall() {
      final String userId = OneSignal.getUserId();
      return userId == null || (nextSyncIsSession && !waitingForSessionResponse);
   }

   static void syncUserState(boolean fromSyncService) {
      final String userId = OneSignal.getUserId();
      boolean isSessionCall =  isSessionCall();

      final JSONObject jsonBody = currentUserState.generateJsonDiff(toSyncUserState, isSessionCall);
      final JSONObject dependDiff = generateJsonDiff(currentUserState.dependValues, toSyncUserState.dependValues, null, null);

      if (jsonBody == null) {
         currentUserState.persistStateAfterSync(dependDiff, null);
         return;
      }
      toSyncUserState.persistState();

      // Prevent non-create player network calls when we don't have a player id yet.
      if (userId == null && !nextSyncIsSession)
         return;

      if (!isSessionCall || fromSyncService) {
         OneSignalRestClient.putSync("players/" + userId, jsonBody, new OneSignalRestClient.ResponseHandler() {
            @Override
            void onFailure(int statusCode, String response, Throwable throwable) {
               OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "Failed last request. statusCode: " + statusCode + "\nresponse: " + response);

               if (response400WithErrorsContaining(statusCode, response, "No user with this id found"))
                  handlePlayerDeletedFromServer();
               else
                  getNetworkHandlerThread(NetworkHandlerThread.NETWORK_HANDLER_USERSTATE).doRetry();
            }

            @Override
            void onSuccess(String response) {
               currentUserState.persistStateAfterSync(dependDiff, jsonBody);
            }
         });
      }
      else {
         String urlStr;
         if (userId == null)
            urlStr = "players";
         else
            urlStr = "players/" + userId + "/on_session";

         waitingForSessionResponse = true;
         OneSignalRestClient.postSync(urlStr, jsonBody, new OneSignalRestClient.ResponseHandler() {
            @Override
            void onFailure(int statusCode, String response, Throwable throwable) {
               waitingForSessionResponse = false;
               OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "Failed last request. statusCode: " + statusCode + "\nresponse: " + response);

               if (response400WithErrorsContaining(statusCode, response, "not a valid device_type"))
                  handlePlayerDeletedFromServer();
               else
                  getNetworkHandlerThread(NetworkHandlerThread.NETWORK_HANDLER_USERSTATE).doRetry();
            }

            @Override
            void onSuccess(String response) {
               nextSyncIsSession = waitingForSessionResponse = false;
               currentUserState.persistStateAfterSync(dependDiff, jsonBody);

               try {
                  JSONObject jsonResponse = new JSONObject(response);

                  if (jsonResponse.has("id")) {
                     String userId = jsonResponse.optString("id");
                     OneSignal.updateUserIdDependents(userId);

                     OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "Device registered, UserId = " + userId);
                  }
                  else
                     OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "session sent, UserId = " + OneSignal.getUserId());

                  OneSignal.updateOnSessionDependents();
               } catch (Throwable t) {
                  OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "ERROR parsing on_session or create JSON Response.", t);
               }
            }
         });
      }
   }

   private static boolean response400WithErrorsContaining(int statusCode, String response, String contains) {
      if (statusCode == 400 && response != null) {
         try {
            JSONObject responseJson = new JSONObject(response);
            return responseJson.has("errors") && responseJson.optString("errors").contains(contains);
         } catch (Throwable t) {
            t.printStackTrace();
         }
      }

      return false;
   }

   private static NetworkHandlerThread getNetworkHandlerThread(Integer type) {
      synchronized (networkHandlerSyncLock) {
         if (!networkHandlerThreads.containsKey(type))
            networkHandlerThreads.put(type, new NetworkHandlerThread(type));
         return networkHandlerThreads.get(type);
      }
   }

   private static UserState getUserStateForModification() {
      if (toSyncUserState == null)
         toSyncUserState = currentUserState.deepClone("TOSYNC_STATE");

      postNewSyncUserState();

      return toSyncUserState;
   }

   private static void postNewSyncUserState() {
      getNetworkHandlerThread(NetworkHandlerThread.NETWORK_HANDLER_USERSTATE).runNewJob();
   }

   static void postUpdate(UserState postSession, boolean isSession) {
      JSONObject toSync = getUserStateForModification().syncValues;
      generateJsonDiff(toSync, postSession.syncValues, toSync, null);
      JSONObject dependValues = getUserStateForModification().dependValues;
      generateJsonDiff(dependValues, postSession.dependValues, dependValues, null);

      nextSyncIsSession = nextSyncIsSession || isSession || OneSignal.getUserId() == null;
   }

   static void sendTags(JSONObject newTags) {
      JSONObject userStateTags = getUserStateForModification().syncValues;
      try {
         generateJsonDiff(userStateTags, new JSONObject().put("tags", newTags), userStateTags, null);
      } catch (JSONException e) { e.printStackTrace(); }
   }

   static void syncHashedEmail(String email) {
      JSONObject syncValues = getUserStateForModification().syncValues;
      try {
         JSONObject emailFields = new JSONObject();
         emailFields.put("em_m", hexDigest(email, "MD5"));
         emailFields.put("em_s", hexDigest(email, "SHA-1"));

         generateJsonDiff(syncValues, emailFields, syncValues, null);
      } catch (Throwable t) { t.printStackTrace(); }
   }

   private static String hexDigest(String str, String digestInstance) throws Throwable {
      MessageDigest digest = java.security.MessageDigest.getInstance(digestInstance);
      digest.update(str.getBytes("UTF-8"));
      byte messageDigest[] = digest.digest();

      StringBuilder hexString = new StringBuilder();
      for (byte aMessageDigest : messageDigest) {
         String h = Integer.toHexString(0xFF & aMessageDigest);
         while (h.length() < 2)
            h = "0" + h;
         hexString.append(h);
      }
      return hexString.toString();
   }

   static void setSubscription(boolean enable) {
      try {
         getUserStateForModification().dependValues.put("userSubscribePref", enable);
      } catch (JSONException e) {
         e.printStackTrace();
      }
   }
   
   static boolean getUserSubscribePreference() {
      return getToSyncUserState().dependValues.optBoolean("userSubscribePref", true);
   }
   
   static void setPermission(boolean enable) {
      try {
         getUserStateForModification().dependValues.put("androidPermission", enable);
      } catch (JSONException e) {
         e.printStackTrace();
      }
   }

   static void updateLocation(LocationGMS.LocationPoint point) {
      UserState userState = getUserStateForModification();
      userState.setLocation(point);
   }

   static boolean getSubscribed() {
      return getToSyncUserState().getNotificationTypes() > 0;
   }


   static String getRegistrationId() {
      return getToSyncUserState().syncValues.optString("identifier", null);
   }

   static class GetTagsResult {
      public boolean serverSuccess;
      public JSONObject result;

      GetTagsResult(boolean serverSuccess, JSONObject result) {
         this.serverSuccess = serverSuccess; this.result = result;
      }
   }

   private static boolean serverSuccess;
   static GetTagsResult getTags(boolean fromServer) {
      if (fromServer) {
         String userId = OneSignal.getUserId();
         String appId = OneSignal.getSavedAppId();
         
         OneSignalRestClient.getSync("players/" + userId + "?app_id=" + appId, new OneSignalRestClient.ResponseHandler() {
            @Override
            void onSuccess(String responseStr) {
               serverSuccess = true;
               try {
                  JSONObject lastGetTagsResponse = new JSONObject(responseStr);
                  if (lastGetTagsResponse.has("tags")) {
                     synchronized(syncLock) {
                        JSONObject dependDiff = generateJsonDiff(currentUserState.syncValues.optJSONObject("tags"),
                            toSyncUserState.syncValues.optJSONObject("tags"),
                            null, null);
   
                        currentUserState.syncValues.put("tags", lastGetTagsResponse.optJSONObject("tags"));
                        currentUserState.persistState();
   
                        // Allow server side tags to overwrite local tags expect for any pending changes
                        //  that haven't been successfully posted.
                        toSyncUserState.mergeTags(lastGetTagsResponse, dependDiff);
                        toSyncUserState.persistState();
                     }
                  }
               } catch (JSONException e) {
                  e.printStackTrace();
               }
            }
         });
      }
   
      synchronized(syncLock) {
         return new GetTagsResult(serverSuccess, getTagsWithoutDeletedKeys(getToSyncUserState().syncValues));
      }
   }

   static void resetCurrentState() {
      OneSignal.saveUserId(null);

      currentUserState.syncValues = new JSONObject();
      currentUserState.persistState();
      OneSignal.setLastSessionTime(-60 * 61);
   }

   private static void handlePlayerDeletedFromServer() {
      resetCurrentState();
      nextSyncIsSession = true;
      postNewSyncUserState();
   }
}