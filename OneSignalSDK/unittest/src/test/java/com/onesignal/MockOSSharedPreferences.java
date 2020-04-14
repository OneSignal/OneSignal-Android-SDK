package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.HashMap;
import java.util.Set;

public class MockOSSharedPreferences extends OneSignalPackagePrivateHelper.OSSharedPreferencesWrapper {

    private HashMap<String, Object> preferences = new HashMap<>();

    public MockOSSharedPreferences() {
    }

    @Override
    public String getOutcomesV2KeyName() {
        return super.getOutcomesV2KeyName();
    }

    @Override
    public String getPreferencesName() {
        return super.getPreferencesName();
    }

    @Override
    public String getString(String prefsName, String key, String defValue) {
        return preferences.get(key) == null ? defValue : (String) preferences.get(key);
    }

    @Override
    public void saveString(String prefsName, String key, String value) {
        preferences.put(key, value);
    }

    @Override
    public boolean getBool(String prefsName, String key, boolean defValue) {
        return preferences.get(key) == null ? defValue : (boolean) preferences.get(key);
    }

    @Override
    public void saveBool(String prefsName, String key, boolean value) {
        preferences.put(key, value);
    }

    @Override
    public int getInt(String prefsName, String key, int defValue) {
        return preferences.get(key) == null ? defValue : (int) preferences.get(key);
    }

    @Override
    public void saveInt(String prefsName, String key, int value) {
        preferences.put(key, value);
    }

    @Override
    public long getLong(String prefsName, String key, long defValue) {
        return preferences.get(key) == null ? defValue : (long) preferences.get(key);
    }

    @Override
    public void saveLong(String prefsName, String key, long value) {
        preferences.put(key, value);
    }

    @Nullable
    @Override
    public Set<String> getStringSet(@NonNull String prefsName, @NonNull String key, @Nullable Set<String> defValue) {
        return preferences.get(key) == null ? defValue : (Set<String>) preferences.get(key);
    }

    @Override
    public void saveStringSet(@NonNull String prefsName, @NonNull String key, @NonNull Set<String> value) {
        preferences.put(key, value);
    }

    @Override
    public Object getObject(String prefsName, String key, Object defValue) {
        return preferences.get(key);
    }

    @Override
    public void saveObject(String prefsName, String key, Object value) {
        preferences.put(key, value);
    }
}
