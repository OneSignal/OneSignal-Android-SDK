package com.onesignal.language;

import com.onesignal.OSUtils;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalPrefs;

public class LanguageProviderAppDefined implements LanguageProvider{
    public static final String PREFS_OS_LANGUAGE = "PREFS_OS_LANGUAGE";

    public void setLanguage(String language) {
        OneSignalPrefs.saveString(
                OneSignalPrefs.PREFS_ONESIGNAL,
                PREFS_OS_LANGUAGE,
                language);
    }

    public String getLanguage() {
        return OneSignalPrefs.getString(
                OneSignalPrefs.PREFS_ONESIGNAL, PREFS_OS_LANGUAGE,"en");
    }
}
