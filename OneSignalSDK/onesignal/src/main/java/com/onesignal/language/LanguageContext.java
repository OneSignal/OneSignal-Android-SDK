package com.onesignal.language;

import com.onesignal.OSUtils;
import com.onesignal.OneSignalPrefs;

import static com.onesignal.language.LanguageProviderAppDefined.PREFS_OS_LANGUAGE;

/*
The Interface implements a getter and setter for the Language Provider.
It defaults to the device defined Language unless a language override is set.
 */
public class LanguageContext {
    private LanguageProvider strategy;

    public LanguageContext() {
        if ( OneSignalPrefs.getString(
                OneSignalPrefs.PREFS_ONESIGNAL, PREFS_OS_LANGUAGE,"en") != null ) {
            this.strategy = new LanguageProviderAppDefined();
        }
        else {
            this.strategy = new LanguageProviderDevice();
        }
    }

    public void setStrategy(LanguageProvider strategy) {
        this.strategy = strategy;
    }

    public String getStrategy() {
        return strategy.getLanguage();
    }
}
