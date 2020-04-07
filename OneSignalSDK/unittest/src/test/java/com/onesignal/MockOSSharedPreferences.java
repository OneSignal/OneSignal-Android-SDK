package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.HashMap;
import java.util.Set;

public class MockOSSharedPreferences extends OSSharedPreferencesWrapper {

    private HashMap<String, Object> preferences = new HashMap<>();
    public boolean mock = false;

    public MockOSSharedPreferences() {
    }

    public void reset() {
        mock = false;
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
        if (mock)
            return preferences.get(key) == null ? defValue : (String) preferences.get(key);
        return super.getString(prefsName, key, defValue);
    }

    @Override
    public void saveString(String prefsName, String key, String value) {
        if (mock) {
            preferences.put(key, value);
        } else super.saveString(prefsName, key, value);
    }

    @Override
    public boolean getBool(String prefsName, String key, boolean defValue) {
        if (mock)
            return preferences.get(key) == null ? defValue : (boolean) preferences.get(key);
        return super.getBool(prefsName, key, defValue);
    }

    @Override
    public void saveBool(String prefsName, String key, boolean value) {
        if (mock) {
            preferences.put(key, value);
        } else super.saveBool(prefsName, key, value);
    }

    @Override
    public int getInt(String prefsName, String key, int defValue) {
        if (mock)
            return preferences.get(key) == null ? defValue : (int) preferences.get(key);
        return super.getInt(prefsName, key, defValue);
    }

    @Override
    public void saveInt(String prefsName, String key, int value) {
        if (mock) {
            preferences.put(key, value);
        } else super.saveInt(prefsName, key, value);
    }

    @Override
    public long getLong(String prefsName, String key, long defValue) {
        if (mock)
            return preferences.get(key) == null ? defValue : (long) preferences.get(key);
        return super.getLong(prefsName, key, defValue);
    }

    @Override
    public void saveLong(String prefsName, String key, long value) {
        if (mock) {
            preferences.put(key, value);
        } else super.saveLong(prefsName, key, value);
    }

    @Nullable
    @Override
    public Set<String> getStringSet(@NonNull String prefsName, @NonNull String key, @Nullable Set<String> defValue) {
        if (mock)
            return preferences.get(key) == null ? defValue : (Set<String>) preferences.get(key);
        return super.getStringSet(prefsName, key, defValue);
    }

    @Override
    public void saveStringSet(@NonNull String prefsName, @NonNull String key, @NonNull Set<String> value) {
        if (mock) {
            preferences.put(key, value);
        } else super.saveStringSet(prefsName, key, value);
    }

    @Override
    public Object getObject(String prefsName, String key, Object defValue) {
        if (mock)
            return preferences.get(key);
        return super.getObject(prefsName, key, defValue);
    }

    @Override
    public void saveObject(String prefsName, String key, Object value) {
        if (mock) {
            preferences.put(key, value);
        } else super.saveObject(prefsName, key, value);
    }
}
