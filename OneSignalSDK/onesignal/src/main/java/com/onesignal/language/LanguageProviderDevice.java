package com.onesignal.language;

import java.util.Locale;

public class LanguageProviderDevice implements LanguageProvider {
    public String getLanguage() {
        String lang = Locale.getDefault().getLanguage();

        // https://github.com/OneSignal/OneSignal-Android-SDK/issues/64
        if (lang.equals("iw"))
            return "he";
        if (lang.equals("in"))
            return "id";
        if (lang.equals("ji"))
            return "yi";

        // https://github.com/OneSignal/OneSignal-Android-SDK/issues/98
        if (lang.equals("zh"))
            return lang + "-" + Locale.getDefault().getCountry();

        return lang;
    }
}
