package com.onesignal.usersdktest.callback;

import com.onesignal.usersdktest.type.OutcomeEvent;

public interface SendOutcomeAlertDialogCallback {

    boolean onSuccess(OutcomeEvent outcomeEvent, String name, String value);
    void onFailure();

}
