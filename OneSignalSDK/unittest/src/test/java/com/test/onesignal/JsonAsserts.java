package com.test.onesignal;

import android.support.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

import static com.onesignal.OneSignalPackagePrivateHelper.JSONUtils.normalizeType;
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

   static void equals(@NonNull JSONObject expected, @NonNull JSONObject actual) throws JSONException {
      assertEquals(expected.length(), actual.length());
      containsSubset(actual, expected);
   }
}
