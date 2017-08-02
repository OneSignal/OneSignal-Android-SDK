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

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.RequiresApi;

public interface BundleCompat<T> {
   void putString(String key, String value);
   void putInt(String key, Integer value);
   void putLong(String key, Long value);
   void putBoolean(String key, Boolean value);
   
   String getString(String key);
   Integer getInt(String key);
   Long getLong(String key);
   boolean getBoolean(String key);
   boolean getBoolean(String key, boolean value);
   
   boolean containsKey(String key);
   
   T getBundle();
}

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
class BundleCompatPersistableBundle implements BundleCompat<PersistableBundle> {
   private PersistableBundle mBundle;
   
   BundleCompatPersistableBundle() {
      mBundle = new PersistableBundle();
   }
   
   BundleCompatPersistableBundle(PersistableBundle bundle) {
      mBundle = bundle;
   }
   
   @Override
   public void putString(String key, String value) {
      mBundle.putString(key, value);
   }
   
   @Override
   public void putInt(String key, Integer value) {
      mBundle.putInt(key, value);
   }

   @Override
   public void putLong(String key, Long value) {
      mBundle.putLong(key, value);
   }
   
   @Override
   public void putBoolean(String key, Boolean value) {
      mBundle.putBoolean(key, value);
   }
   
   @Override
   public String getString(String key) {
      return mBundle.getString(key);
   }
   
   @Override
   public Integer getInt(String key) {
      return mBundle.getInt(key);
   }
   
   @Override
   public Long getLong(String key) {
      return mBundle.getLong(key);
   }
   
   @Override
   public boolean getBoolean(String key) {
      return mBundle.getBoolean(key);
   }
   
   @Override
   public boolean getBoolean(String key, boolean value) {
      return mBundle.getBoolean(key, value);
   }
   
   @Override
   public boolean containsKey(String key) {
      return mBundle.containsKey(key);
   }
   
   @Override
   public PersistableBundle getBundle() {
      return mBundle;
   }
}

class BundleCompatBundle implements BundleCompat<Bundle> {
   private Bundle mBundle;
   
   BundleCompatBundle() {
      mBundle = new Bundle();
   }
   
   BundleCompatBundle(Bundle bundle) {
      mBundle = bundle;
   }
   
   BundleCompatBundle(Intent intent) {
      mBundle = intent.getExtras();
   }
   
   @Override
   public void putString(String key, String value) {
      mBundle.putString(key, value);
   }

   @Override
   public void putInt(String key, Integer value) {
      mBundle.putInt(key, value);
   }
   
   @Override
   public void putLong(String key, Long value) {
      mBundle.putLong(key, value);
   }
   
   @Override
   public void putBoolean(String key, Boolean value) {
      mBundle.putBoolean(key, value);
   }
   
   @Override
   public String getString(String key) {
      return mBundle.getString(key);
   }
   
   @Override
   public Integer getInt(String key) {
      return mBundle.getInt(key);
   }
   
   @Override
   public Long getLong(String key) {
      return mBundle.getLong(key);
   }
   
   @Override
   public boolean getBoolean(String key) {
      return mBundle.getBoolean(key);
   }
   
   @Override
   public boolean containsKey(String key) {
      return mBundle.containsKey(key);
   }
   
   @Override
   public Bundle getBundle() {
      return mBundle;
   }
   
   @Override
   public boolean getBoolean(String key, boolean value) {
     return mBundle.getBoolean(key, value);
   }
}

class BundleCompatFactory {
   static BundleCompat getInstance() {
      if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
         return new BundleCompatPersistableBundle();
      return new BundleCompatBundle();
   }
}