package com.onesignal.language;

import com.onesignal.OSUtils;

public class LanguageProviderDevice implements LanguageProvider{
    public String getLanguage() {
        return OSUtils.getCorrectedLanguage();
    }
}
