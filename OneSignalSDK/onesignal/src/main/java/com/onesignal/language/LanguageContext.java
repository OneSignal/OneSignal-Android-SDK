package com.onesignal.language;
import com.onesignal.OneSignal;
import static com.onesignal.language.LanguageProviderAppDefined.PREFS_OS_LANGUAGE;

/*
The Interface implements a getter and setter for the Language Provider.
It defaults to the device defined Language unless a language override is set.
 */
public class LanguageContext {
    private LanguageProvider strategy;

    public LanguageContext() {
        if ( OneSignal.preferences.getString(
                OneSignal.preferences.getPreferencesName(), PREFS_OS_LANGUAGE, null) != null) {
            this.strategy = new LanguageProviderAppDefined();
        }
        else {
            this.strategy = new LanguageProviderDevice();
        }
    }

    public void setStrategy(LanguageProvider strategy) {
        this.strategy = strategy;
    }

    public String getLanguage() {
        return strategy.getLanguage();
    }
}
