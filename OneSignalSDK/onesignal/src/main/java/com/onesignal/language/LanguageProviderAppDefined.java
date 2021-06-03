package com.onesignal.language;
import android.support.annotation.NonNull;

import com.onesignal.OSSharedPreferences;

public class LanguageProviderAppDefined implements LanguageProvider {
    public static final String PREFS_OS_LANGUAGE = "PREFS_OS_LANGUAGE";

    private static final String DEFAULT_LANGUAGE = "en";
    private final OSSharedPreferences preferences;

    public LanguageProviderAppDefined(OSSharedPreferences preferences) {
        this.preferences = preferences;
    }

    public void setLanguage(String language) {
        preferences.saveString(
                preferences.getPreferencesName(),
                PREFS_OS_LANGUAGE,
                language);
    }

    @NonNull
    public String getLanguage() {
        return preferences.getString(
                preferences.getPreferencesName(), PREFS_OS_LANGUAGE, DEFAULT_LANGUAGE);
    }
}
