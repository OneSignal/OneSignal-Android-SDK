package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Set;

public interface OSSharedPreferences {

    String getOutcomesV2KeyName();

    String getPreferencesName();

    String getString(String prefsName, String key, String defValue);

    void saveString(final String prefsName, final String key, final String value);

    boolean getBool(String prefsName, String key, boolean defValue);

    void saveBool(String prefsName, String key, boolean value);

    int getInt(String prefsName, String key, int defValue);

    void saveInt(String prefsName, String key, int value);

    long getLong(String prefsName, String key, long defValue);

    void saveLong(String prefsName, String key, long value);

    @Nullable
    Set<String> getStringSet(@NonNull String prefsName, @NonNull String key, @Nullable Set<String> defValue);

    void saveStringSet(@NonNull final String prefsName, @NonNull final String key, @NonNull final Set<String> value);

    Object getObject(String prefsName, String key, Object defValue);

    void saveObject(String prefsName, String key, Object value);

}
