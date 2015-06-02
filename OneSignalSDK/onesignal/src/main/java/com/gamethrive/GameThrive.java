/**
 * Modified MIT License
 * 
 * Copyright 2015 OneSignal
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

package com.gamethrive;

import java.math.BigDecimal;
import java.util.Collection;

import org.json.JSONObject;

import android.app.Activity;
import android.util.Log;

import com.onesignal.OneSignal;

/**
 * @deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
 * @Deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
 */
public class GameThrive {

   /**
    * @deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    * @Deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    */
   public interface IdsAvailableHandler extends OneSignal.IdsAvailableHandler {
   }

   /**
    * @deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    * @Deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    */
   public interface GetTagsHandler extends OneSignal.GetTagsHandler {
   }

   /**
    * @deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    * @Deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    */
   public static final int VERSION = 010700;
   
   /**
    * @deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    * @Deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    */
   public static final String STRING_VERSION = "010700";

   /**
    * @deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    * @Deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    */
   public GameThrive(Activity context, String googleProjectNumber, String gameThriveAppId) {
      this(context, googleProjectNumber, gameThriveAppId, null);
   }

   /**
    * @deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    * @Deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    */
   public GameThrive(Activity context, String googleProjectNumber, String gameThriveAppId, NotificationOpenedHandler notificationOpenedHandler) {
      Log.w("GameThrive", "WARNING: GameThrive is depercated! GameThrive is now OneSignal, please use the new class as soon as possible!");
      OneSignal.init(context, googleProjectNumber, gameThriveAppId, notificationOpenedHandler);
   }

   /**
    * @deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    * @Deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    */
   public void onPaused() {
      OneSignal.onPaused();
   }

   /**
    * @deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    * @Deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    */
   public void onResumed() {
      OneSignal.onResumed();
   }

   /**
    * @deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    * @Deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    */
   public void sendTag(String key, String value) {
      OneSignal.sendTag(key, value);
   }

   /**
    * @deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    * @Deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    */
   public void sendTags(String jsonString) {
      OneSignal.sendTags(jsonString);
   }

   /**
    * @deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    * @Deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    */
   public void sendTags(JSONObject keyValues) {
      OneSignal.sendTags(keyValues);
   }

   /**
    * @deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    * @Deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    */
   public void getTags(final GetTagsHandler getTagsHandler) {
      OneSignal.getTags(getTagsHandler);
   }

   /**
    * @deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    * @Deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    */
   public void deleteTag(String key) {
      OneSignal.deleteTag(key);
   }

   /**
    * @deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    * @Deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    */
   public void deleteTags(Collection<String> keys) {
      OneSignal.deleteTags(keys);
   }

   /**
    * @deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    * @Deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    */
   public void deleteTags(String jsonArrayString) {
      OneSignal.deleteTags(jsonArrayString);
   }

   /**
    * @deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    * @Deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    */
   public void idsAvailable(IdsAvailableHandler idsAvailableHandler) {
      OneSignal.idsAvailable(idsAvailableHandler);
   }

   /**
    * Call when player makes an IAP purchase in your app with the amount in USD.
    *
    * @deprecated Automatically tracked.
    * @Deprecated Automatically tracked.
    */
   public void sendPurchase(double amount) {
      Log.i("OneSignal", "sendPurchase is deprecated as this is now automatic for Google Play IAP purchases. The method does nothing!");
   }

   /**
    * Call when player makes an IAP purchase in your app with the amount in USD.
    *
    * @deprecated Automatically tracked.
    * @Deprecated Automatically tracked.
    */
   public void sendPurchase(BigDecimal amount) {
      Log.i("OneSignal", "sendPurchase is deprecated as this is now automatic for Google Play IAP purchases. The method does nothing!");
   }

   // If true(default) - Device will always vibrate unless the device is in silent mode.
   // If false - Device will only vibrate when the device is set on it's vibrate only mode.
   /**
    * @deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    * @Deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    */
   public void enableVibrate(boolean enable) {
      OneSignal.enableVibrate(enable);
   }

   // If true(default) - Sound plays when receiving notification. Vibrates when device is on vibrate only mode.
   // If false - Only vibrates unless EnableVibrate(false) was set.
   /**
    * @deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    * @Deprecated WARNING: GameThrive is deprecated! GameThrive is now OneSignal, please use the new class as soon as possible!
    */
   public void enableSound(boolean enable) {
      OneSignal.enableSound(enable);
   }
}
