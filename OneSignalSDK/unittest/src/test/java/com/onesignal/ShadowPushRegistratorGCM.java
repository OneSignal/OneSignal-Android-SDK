/**
 * Modified MIT License
 *
 * Copyright 2018 OneSignal
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

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

import static org.robolectric.shadow.api.Shadow.directlyOn;

@Implements(PushRegistratorAbstractGoogle.class)
public class ShadowPushRegistratorGCM {
   
   @RealObject private PushRegistratorAbstractGoogle realInstance;
   
   public static final String regId = "aspdfoh0fhj02hr-2h";

   public static boolean fail;
   public static boolean skipComplete;
   public static String lastProjectNumber;

   private static PushRegistrator.RegisteredHandler lastCallback;

   public static void resetStatics() {
      skipComplete = false;
      fail = false;
      lastProjectNumber = null;
      lastCallback = null;
   }

   public static void manualFireRegisterForPush() {
      lastCallback.complete(regId, UserState.PUSH_STATUS_SUBSCRIBED);
   }
   
   @Implementation
   public void registerForPush(Context context, String senderId, PushRegistrator.RegisteredHandler callback) {
      PushRegistratorAbstractGoogle pushRegistrator = directlyOn(realInstance, PushRegistratorAbstractGoogle.class);
      pushRegistrator.registerForPush(context, senderId, callback);
      
      lastProjectNumber = senderId;
      lastCallback = callback;

      if (!skipComplete)
         callback.complete(fail ? null : regId, fail ? -7 : 1);
   }

   @Implementation
   public void internalRegisterForPush(String senderId) {}
   

   public static void fireLastCallback() {
      lastCallback.complete(fail ? null : regId, fail ? -7 : 1);
   }
}
