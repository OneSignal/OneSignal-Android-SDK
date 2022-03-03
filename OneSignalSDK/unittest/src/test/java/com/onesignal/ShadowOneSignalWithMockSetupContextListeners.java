package com.onesignal;

import com.onesignal.language.LanguageContext;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * This shadow is added for the test initWithContext_setupContextListenersNotCompleted_doesNotProduceNPE
 * Changes the behavior of one method without affecting other unit tests using ShadowOneSignal
 */
@Implements(OneSignal.class)
public class ShadowOneSignalWithMockSetupContextListeners {

   /**
    * Simulates setupContextListeners() in initWithContext() not completing.
    * However, languageContext initialization is needed for later, so that is the only code kept
    */
   @Implementation
   public static void setupContextListeners(boolean wasAppContextNull) {

      // Do work here that should only happen once or at the start of a new lifecycle
      if (wasAppContextNull) {
         OneSignal.languageContext = new LanguageContext(OneSignal.getSharedPreferences());
      }
   }
}
