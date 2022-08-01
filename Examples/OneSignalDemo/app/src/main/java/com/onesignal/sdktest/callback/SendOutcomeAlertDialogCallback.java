package com.onesignal.sdktest.callback;

import com.onesignal.sdktest.type.OutcomeEvent;

public interface SendOutcomeAlertDialogCallback {

    boolean onSuccess(OutcomeEvent outcomeEvent, String name, String value);
    void onFailure();

}
