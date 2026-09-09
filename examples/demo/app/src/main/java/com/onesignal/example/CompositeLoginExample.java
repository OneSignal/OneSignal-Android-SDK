package com.onesignal.example;

import com.onesignal.OneSignalUserProfile;
import java.util.Collections;
import java.util.Map;

/** Java example of building a composite-login profile with OneSignalUserProfile.Builder. */
public final class CompositeLoginExample {
    private CompositeLoginExample() {}

    public static OneSignalUserProfile profile(String email, String phoneNumber) {
        return profile(email, phoneNumber, Collections.emptyMap(), Collections.emptyMap());
    }

    public static OneSignalUserProfile profile(
            String email,
            String phoneNumber,
            Map<String, String> tags,
            Map<String, String> aliases) {
        return new OneSignalUserProfile.Builder()
                .setEmail(email)
                .setPhoneNumber(phoneNumber)
                .setTags(tags)
                .setAliases(aliases)
                .build();
    }
}
