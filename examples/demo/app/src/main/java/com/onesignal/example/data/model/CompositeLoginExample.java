package com.onesignal.example.data.model;

import com.onesignal.OneSignalUserProfile;
import java.util.Collections;
import java.util.Map;

/** Java example of building a composite-login profile with OneSignalUserProfile.Builder. */
public final class CompositeLoginExample {
    private CompositeLoginExample() {}

    public static OneSignalUserProfile profile(String email, String sms) {
        return profile(email, sms, Collections.emptyMap(), Collections.emptyMap());
    }

    public static OneSignalUserProfile profile(
            String email,
            String sms,
            Map<String, String> tags,
            Map<String, String> aliases) {
        return profile(email, sms, tags, aliases, null, null);
    }

    public static OneSignalUserProfile profile(
            String email,
            String sms,
            Map<String, String> tags,
            Map<String, String> aliases,
            Boolean emailEnabled,
            Boolean smsEnabled) {
        OneSignalUserProfile.Builder builder = new OneSignalUserProfile.Builder()
                .setEmail(email)
                .setSms(sms)
                .setTags(tags)
                .setAliases(aliases);
        // Leaving a status unset keeps an address that already exists as it is.
        if (emailEnabled != null) {
            builder.setEmailEnabled(emailEnabled);
        }
        if (smsEnabled != null) {
            builder.setSmsEnabled(smsEnabled);
        }
        return builder.build();
    }
}
