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

import org.json.JSONObject;

public class OSPermissionSubscriptionState {
   OSSubscriptionState subscriptionStatus;
   OSPermissionState permissionStatus;
   OSEmailSubscriptionState emailSubscriptionStatus;

   public OSPermissionState getPermissionStatus() {
      return permissionStatus;
   }
   
   public OSSubscriptionState getSubscriptionStatus() {
      return subscriptionStatus;
   }

   public OSEmailSubscriptionState getEmailSubscriptionStatus() {
      return emailSubscriptionStatus;
   }
   
   public JSONObject toJSONObject() {
      JSONObject mainObj = new JSONObject();
      
      try {
         mainObj.put("permissionStatus", permissionStatus.toJSONObject());
         mainObj.put("subscriptionStatus", subscriptionStatus.toJSONObject());
         mainObj.put("emailSubscriptionStatus", emailSubscriptionStatus.toJSONObject());
      }
      catch(Throwable t) {
         t.printStackTrace();
      }
      
      return mainObj;
   }
   
   @Override
   public String toString() {
      return toJSONObject().toString();
   }
}
