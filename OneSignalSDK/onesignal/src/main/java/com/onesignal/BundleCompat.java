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