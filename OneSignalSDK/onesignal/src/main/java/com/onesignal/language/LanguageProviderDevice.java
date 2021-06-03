package com.onesignal.language;

import android.support.annotation.NonNull;

import java.util.Locale;

public class LanguageProviderDevice implements LanguageProvider {
    private static final String HEBREW_INCORRECT = "iw";
    private static final String HEBREW_CORRECTED = "he";
    private static final String INDONESIAN_INCORRECT = "in";
    private static final String INDONESIAN_CORRECTED = "id";
    private static final String YIDDISH_INCORRECT = "ji";
    private static final String YIDDISH_CORRECTED = "yi";
    private static final String CHINESE = "zh";

    @NonNull
    public String getLanguage() {
        String language = Locale.getDefault().getLanguage();

        switch (language) {
            // https://github.com/OneSignal/OneSignal-Android-SDK/issues/64
            case HEBREW_INCORRECT:
                return HEBREW_CORRECTED;
            case INDONESIAN_INCORRECT:
                return INDONESIAN_CORRECTED;
            case YIDDISH_INCORRECT:
                return YIDDISH_CORRECTED;
            // https://github.com/OneSignal/OneSignal-Android-SDK/issues/98
            case CHINESE:
                return language + "-" + Locale.getDefault().getCountry();
            default:
                return language;
        }
    }
}
