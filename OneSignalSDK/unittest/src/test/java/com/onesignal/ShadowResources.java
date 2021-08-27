package com.onesignal;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(value = android.content.res.Resources.class)
public class ShadowResources {

    // Returns 0 to mimic no resources found
    @Implementation
    public int getIdentifier(String name, String defType, String defPackage) {
        return 0;
    }
}
