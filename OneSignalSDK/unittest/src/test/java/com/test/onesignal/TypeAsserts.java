package com.test.onesignal;

import android.support.annotation.Nullable;

import java.util.UUID;

class TypeAsserts {

   static void assertIsUUID(@Nullable String value) {
      UUID.fromString(value);
   }
}
