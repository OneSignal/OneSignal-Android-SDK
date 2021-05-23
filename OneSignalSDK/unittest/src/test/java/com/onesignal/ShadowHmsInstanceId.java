package com.onesignal;

import android.content.Context;

import androidx.annotation.Nullable;

import com.huawei.hms.aaid.HmsInstanceId;
import com.huawei.hms.common.ApiException;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(HmsInstanceId.class)
public class ShadowHmsInstanceId {

    static final String DEFAULT_MOCK_HMS_TOKEN_VALUE = "MockHMSToken";

    public static @Nullable String token;
    public static @Nullable ApiException throwException;

    public static void resetStatics() {
        token = DEFAULT_MOCK_HMS_TOKEN_VALUE;
        throwException = null;
    }

    @Implementation
    public void __constructor__(Context context) {
    }

    @Implementation
    public String getToken(String var1, String var2) throws ApiException {
        if (throwException != null)
            throw throwException;
        return token;
    }
}
