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

import org.json.JSONObject;

import static com.onesignal.OneSignal.appContext;

public class OSPermissionState implements Cloneable {
   
   OSObservable<Object, OSPermissionState> observable;
   
   OSPermissionState(boolean asFrom) {
      // Java 8 method reference can be used in the future with Android Studio 2.4.0
      //   OSPermissionChangedInternalObserver::changed
      observable = new OSObservable<>("changed", false);
      
      if (asFrom) {
         enabled = OneSignalPrefs.getBool(OneSignalPrefs.PREFS_ONESIGNAL,
                 OneSignalPrefs.PREFS_ONESIGNAL_ACCEPTED_NOTIFICATION_LAST,false);
      }
      else
         refreshAsTo();
   }
   
   private boolean enabled;
   
   void refreshAsTo() {
      setEnabled(OSUtils.areNotificationsEnabled(appContext));
   }
   
   public boolean getEnabled() {
      return enabled;
   }
   
   private void setEnabled(boolean set) {
      boolean changed = enabled != set;
      enabled = set;
      if (changed)
         observable.notifyChange(this);
   }
   
   void persistAsFrom() {
      OneSignalPrefs.saveBool(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_ONESIGNAL_ACCEPTED_NOTIFICATION_LAST, enabled);
   }
   
   boolean compare(OSPermissionState from) {
      return enabled != from.enabled;
   }
   
   protected Object clone() {
      try {
         return super.clone();
      } catch (Throwable t) {}
      return null;
   }
   
   public JSONObject toJSONObject() {
      JSONObject mainObj = new JSONObject();
      
      try {
         mainObj.put("enabled", enabled);
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
   
   
   // FUTURE: Can add a list of categories here for Android O.
}
