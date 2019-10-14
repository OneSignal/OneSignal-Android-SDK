package com.test.onesignal;

import android.support.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

class JsonAsserts {

   static void doesNotContainKeys(@NonNull JSONObject subject, @NonNull List<String> keys) {
      for(String key : keys)
         assertFalse(subject.has(key));
   }

   // Assert that the "subject" has all the keys with the same values from the "contains".
   static void containsSubset(@NonNull JSONObject subject, @NonNull JSONObject contains) throws JSONException {
      Iterator<String> iterator = contains.keys();
      while (iterator.hasNext()) {
         String key = iterator.next();
         assertEquals(
            normalizeType(contains.get(key)),
            normalizeType(subject.get(key))
         );
      }
   }

   // Converts Java types that are equivalent in the JSON format to the same types.
   // This allows for assertEquals on two values from JSONObject.get to test values as long as it
   //   returns in the same JSON output.
   private static Object normalizeType(Object object) {
      Class clazz = object.getClass();

      if (clazz.equals(Integer.class))
         return Long.valueOf((Integer)object);
      if (clazz.equals(Float.class))
         return Double.valueOf((Float)object);

      return object;
   }
}
