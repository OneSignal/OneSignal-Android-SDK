package com.onesignal.sdktest.callback;

import android.util.Pair;

public interface AddPairAlertDialogCallback {

    void onSuccess(Pair<String, Object> pair);
    void onFailure();

}
