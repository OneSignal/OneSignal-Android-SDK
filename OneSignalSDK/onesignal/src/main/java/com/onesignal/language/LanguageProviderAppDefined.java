package com.onesignal.language;
import com.onesignal.OneSignal;

public class LanguageProviderAppDefined implements LanguageProvider{
    public static final String PREFS_OS_LANGUAGE = "PREFS_OS_LANGUAGE";

    public void setLanguage(String language) {
        OneSignal.preferences.saveString(
                OneSignal.preferences.getPreferencesName(),
                PREFS_OS_LANGUAGE,
                language);
    }

    public String getLanguage() {
        return OneSignal.preferences.getString(
                OneSignal.preferences.getPreferencesName(), PREFS_OS_LANGUAGE, "en");
    }
}
