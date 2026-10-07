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
        return new OneSignalUserProfile.Builder()
                .setEmail(email)
                .setSms(sms)
                .setTags(tags)
                .setAliases(aliases)
                .build();
    }
}
