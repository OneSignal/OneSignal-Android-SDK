package com.onesignal;

import org.json.JSONException;
import org.json.JSONObject;

import static com.onesignal.UserState.CONFIG_ERRORS;
import static com.onesignal.UserState.RUNTIME_ERRORS;

public class OSSubscriptionError {
   int configError = -1, runtimeError = -1;

   OSSubscriptionError(int subscribableStatus) {
      if (CONFIG_ERRORS.contains(subscribableStatus))
         configError = CONFIG_ERRORS.indexOf(subscribableStatus);
      else
         runtimeError = RUNTIME_ERRORS.indexOf(subscribableStatus);
   }

   public int getConfigError() {
      return configError;
   }

   public int getRuntimeError() {
      return runtimeError;
   }

   boolean compare(OSSubscriptionError from) {
      return configError != from.configError || runtimeError != from.runtimeError;
   }

   public JSONObject toJSONObject() {
      JSONObject jsonObject = new JSONObject();
      try {
         if (configError != -1)
            jsonObject.put("configError", configError);
         else
            jsonObject.put("runtimeError", runtimeError);
      } catch (JSONException t) {
         t.printStackTrace();
      }

      return jsonObject;
   }

   @Override
   public String toString() {
      return toJSONObject().toString();
   }
}
