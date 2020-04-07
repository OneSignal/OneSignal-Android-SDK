package com.onesignal.outcomes.domain;

import com.onesignal.OneSignalApiResponseHandler;

import org.json.JSONObject;

public interface OutcomeEventsService {

    void sendOutcomeEvent(JSONObject object, OneSignalApiResponseHandler responseHandler);

}
