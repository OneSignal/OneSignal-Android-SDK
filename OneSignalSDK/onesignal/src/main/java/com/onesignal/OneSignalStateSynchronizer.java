/**
 * Modified MIT License
 *
 * Copyright 2016 OneSignal
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

class OneSignalStateSynchronizer {
   private static boolean onSessionDone = false, postSessionCalled = false, waitingForSessionResponse = false;

   // currentUserState - Current known state of the user on OneSignal's server.
   // toSyncUserState  - Pending state that will be synced to the OneSignal server.
   //                    diff will be generated between currentUserState when a sync call is made to the server.
   private static UserState currentUserState, toSyncUserState;

   static HashMap<Integer, NetworkHandlerThread> networkHandlerThreads = new HashMap<>();

   private static Context appContext;

   private static final String[] LOCATION_FIELDS = new String[] { "lat", "long", "loc_acc", "loc_type"};
   private static final Set<String> LOCATION_FIELDS_SET = new HashSet<String>(Arrays.asList(LOCATION_FIELDS));

   static private JSONObject generateJsonDiff(JSONObject cur, JSONObject changedTo, JSONObject baseOutput, Set<String> includeFields) {
      Iterator<String> keys = changedTo.keys();
      String key;
      Object value;

      JSONObject output;
      if (baseOutput != null)
         output = baseOutput;
      else
         output = new JSONObject();

      synchronized (cur) {
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

         synchronized (jsonObject) {
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
   
   public static void stopAndPersist() {
      for (Map.Entry<Integer, OneSignalStateSynchronizer.NetworkHandlerThread> handlerThread : OneSignalStateSynchronizer.networkHandlerThreads.entrySet())
         handlerThread.getValue().stopScheduledRunnable();

      if (toSyncUserState != null)
         toSyncUserState.persistState();
   }
   
   class UserState {

      private final int UNSUBSCRIBE_VALUE = -2;

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
         try {
            int subscribableStatus = dependValues.getInt("subscribableStatus");
            boolean userSubscribePref = dependValues.getBoolean("userSubscribePref");
            return subscribableStatus < UNSUBSCRIBE_VALUE ? subscribableStatus : (userSubscribePref ? 1 : UNSUBSCRIBE_VALUE);
         } catch (JSONException e) {
            e.printStackTrace();
         }

         return 1;
      }

      private Set<String> getGroupChangeField(JSONObject cur, JSONObject changedTo) {
         try {
            if (cur.getDouble("lat") != changedTo.getDouble("lat")
             || cur.getDouble("long") != changedTo.getDouble("long")
             || cur.getDouble("loc_acc") != changedTo.getDouble("loc_acc")
             || cur.getDouble("loc_type") != changedTo.getDouble("loc_type"))
               return LOCATION_FIELDS_SET;
         } catch (Throwable t) {
            return LOCATION_FIELDS_SET;
         }

         return null;
      }

      private JSONObject generateJsonDiff(UserState newState, boolean isSessionCall) {
         addDependFields(); newState.addDependFields();
         Set<String> includeFields = getGroupChangeField(syncValues, newState.syncValues);
         JSONObject sendJson = OneSignalStateSynchronizer.generateJsonDiff(syncValues, newState.syncValues, null, includeFields);

         if (!isSessionCall && sendJson.toString().equals("{}"))
            return null;

         try {
            // This makes sure app_id is in all our REST calls.
            if (!sendJson.has("app_id"))
               sendJson.put("app_id", (String) syncValues.opt("app_id"));
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
         if (dependValuesStr == null) {
            dependValues = new JSONObject();
            try {
               int subscribableStatus;
               boolean userSubscribePref = true;
               if (persistKey.equals("CURRENT_STATE"))
                  subscribableStatus = prefs.getInt("ONESIGNAL_SUBSCRIPTION", 1);
               else
                  subscribableStatus = prefs.getInt("ONESIGNAL_SYNCED_SUBSCRIPTION", 1);

               if (subscribableStatus == UNSUBSCRIBE_VALUE) {
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
         modifySyncValuesJsonArray("pkgs");

         final SharedPreferences prefs = OneSignal.getGcmPreferences(appContext);
         SharedPreferences.Editor editor = prefs.edit();

         editor.putString("ONESIGNAL_USERSTATE_SYNCVALYES_" + persistKey, syncValues.toString());
         editor.putString("ONESIGNAL_USERSTATE_DEPENDVALYES_" + persistKey, dependValues.toString());
         editor.commit();
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

            if (inSyncValues.has("tags")) {
               JSONObject newTags = new JSONObject();
               JSONObject curTags = inSyncValues.optJSONObject("tags");
               Iterator<String> keys = curTags.keys();
               String key;

               try {
                  while (keys.hasNext()) {
                     key = keys.next();
                     if (!"".equals(curTags.optString(key)))
                        newTags.put(key, curTags.optString(key));
                  }

                  if (newTags.toString().equals("{}"))
                     syncValues.remove("tags");
                  else
                     syncValues.put("tags", newTags);
               } catch (Throwable t) {}
            }
         }

         if (inDependValues != null || inSyncValues != null)
            persistState();
      }
   }

   static class NetworkHandlerThread extends HandlerThread {
      private static final int NETWORK_HANDLER_USERSTATE = 0;

      int mType;

      Handler mHandler = null;

      static final int MAX_RETRIES = 3;
      int currentRetry;

      NetworkHandlerThread(int type) {
         super("NetworkHandlerThread");
         mType = type;
         start();
         mHandler = new Handler(getLooper());
      }
   
      public void runNewJob() {
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

      if (currentUserState != null) return;

      currentUserState = new OneSignalStateSynchronizer().new UserState("CURRENT_STATE", true);
      toSyncUserState = new OneSignalStateSynchronizer().new UserState("TOSYNC_STATE", true);
   }

   static UserState getNewUserState() {
      return new OneSignalStateSynchronizer().new UserState("nonPersist", false);
   }

   static void syncUserState(boolean fromSyncService) {
      boolean isSessionCall = !onSessionDone && postSessionCalled && !waitingForSessionResponse;

      final JSONObject jsonBody = currentUserState.generateJsonDiff(toSyncUserState, isSessionCall);
      final JSONObject dependDiff = generateJsonDiff(currentUserState.dependValues, toSyncUserState.dependValues, null, null);

      if (jsonBody == null) {
         currentUserState.persistStateAfterSync(dependDiff, null);
         return;
      }

      final String userId = OneSignal.getUserId();

      toSyncUserState.persistState();
      if (onSessionDone || fromSyncService) {
         OneSignalRestClient.putSync("players/" + userId, jsonBody, new OneSignalRestClient.ResponseHandler() {
            @Override
            void onFailure(int statusCode, String response, Throwable throwable) {
               OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "Failed last request. statusCode: " + statusCode + "\nresponse: " + response);

               if (response400WithErrorsContaining(statusCode, response, "No user with this id found")) {
                  resetCurrentState();
                  postNewSyncUserState();
               }
               else
                  getNetworkHandlerThread(NetworkHandlerThread.NETWORK_HANDLER_USERSTATE).doRetry();
            }

            @Override
            void onSuccess(String response) {
               currentUserState.persistStateAfterSync(dependDiff, jsonBody);
            }
         });
      }
      else if (postSessionCalled) {
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

               if (response400WithErrorsContaining(statusCode, response, "not a valid device_type")) {
                  resetCurrentState();
                  postNewSyncUserState();
               }
               else
                  getNetworkHandlerThread(NetworkHandlerThread.NETWORK_HANDLER_USERSTATE).doRetry();
            }

            @Override
            void onSuccess(String response) {
               onSessionDone = true;
               waitingForSessionResponse = false;
               currentUserState.persistStateAfterSync(dependDiff, jsonBody);

               try {
                  JSONObject jsonResponse = new JSONObject(response);

                  if (jsonResponse.has("id")) {
                     String userId = jsonResponse.getString("id");
                     OneSignal.updateUserIdDependents(userId);

                     OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "Device registered, UserId = " + userId);
                  } else
                     OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "session sent, UserId = " + OneSignal.getUserId());
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
      if (!networkHandlerThreads.containsKey(type))
         networkHandlerThreads.put(type, new NetworkHandlerThread(type));
      return networkHandlerThreads.get(type);
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

   static void postSession(UserState postSession) {
      JSONObject toSync = getUserStateForModification().syncValues;
      generateJsonDiff(toSync, postSession.syncValues, toSync, null);
      JSONObject dependValues = getUserStateForModification().dependValues;
      generateJsonDiff(dependValues, postSession.dependValues, dependValues, null);

      postSessionCalled = true;
   }

   static void sendTags(JSONObject newTags) {
      JSONObject userStateTags = getUserStateForModification().syncValues;
      try {
         generateJsonDiff(userStateTags, new JSONObject().put("tags", newTags), userStateTags, null);
      } catch (JSONException e) { e.printStackTrace(); }
   }

   static void setEmail(String email) {
      JSONObject syncValues = getUserStateForModification().syncValues;
      try {
         System.out.println("syncValues1: " + syncValues.toString());
         generateJsonDiff(syncValues, new JSONObject().put("email", email), syncValues, null);
         System.out.println("syncValues2: " + syncValues.toString());
      } catch (JSONException e) { e.printStackTrace(); }
   }

   static void setSubscription(boolean enable) {
      try {
         getUserStateForModification().dependValues.put("userSubscribePref", enable);
      } catch (JSONException e) {
         e.printStackTrace();
      }
   }

   static void updateIdentifier(String identifier) {
      UserState userState = getUserStateForModification();
      try {
         userState.syncValues.put("identifier", identifier);
      } catch (JSONException e) {
         e.printStackTrace();
      }
   }

   static void updateLocation(Double lat, Double log, Float accuracy, Integer type) {
      UserState userState = getUserStateForModification();
      try {
         userState.syncValues.put("lat", lat);
         userState.syncValues.put("long", log);
         userState.syncValues.put("loc_acc", accuracy);
         userState.syncValues.put("loc_type", type);
      } catch (JSONException e) {
         e.printStackTrace();
      }
   }

   static boolean getSubscribed() {
      return toSyncUserState.getNotificationTypes() > 0;
   }


   static String getRegistrationId() {
      return toSyncUserState.syncValues.optString("identifier", null);
   }

   static class GetTagsResult {
      public boolean serverSuccess;
      public JSONObject result;

      GetTagsResult(boolean serverSuccess, JSONObject result) {
         this.serverSuccess = serverSuccess; this.result = result;
      }
   }

   private static JSONObject lastGetTagsResponse;
   static GetTagsResult getTags(boolean fromServer) {
      lastGetTagsResponse = null;

      if (fromServer) {
         String userId = OneSignal.getUserId();
         OneSignalRestClient.getSync("players/" + userId, new OneSignalRestClient.ResponseHandler() {
            @Override
            void onSuccess(String responseStr) {
               try {
                  lastGetTagsResponse = new JSONObject(responseStr);
                  if (lastGetTagsResponse.has("tags")) {
                     lastGetTagsResponse = lastGetTagsResponse.optJSONObject("tags");
                     currentUserState.syncValues.put("tags", lastGetTagsResponse);
                     currentUserState.persistState();

                     JSONObject tagsToSync = getTagsWithoutDeletedKeys(toSyncUserState.syncValues);
                     if (tagsToSync != null) {
                        Iterator<String> keys = tagsToSync.keys();
                        while (keys.hasNext()) {
                           String key = keys.next();
                           lastGetTagsResponse.put(key, tagsToSync.optString(key));
                        }
                     }
                  }
                  else
                     lastGetTagsResponse = null;
               } catch (JSONException e) {
                  e.printStackTrace();
               }
            }
         });
      }

      if (lastGetTagsResponse == null)
         return new GetTagsResult(false, getTagsWithoutDeletedKeys(toSyncUserState.syncValues));
      return new GetTagsResult(true, lastGetTagsResponse);
   }

   static void resetCurrentState() {
      onSessionDone = false;
      OneSignal.saveUserId(null);

      currentUserState.syncValues = new JSONObject();
      currentUserState.persistState();
   }
}