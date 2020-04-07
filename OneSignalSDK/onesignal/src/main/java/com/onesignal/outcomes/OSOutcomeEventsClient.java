package com.onesignal.outcomes;

import com.onesignal.OneSignalAPIClient;
import com.onesignal.OneSignalApiResponseHandler;
import com.onesignal.outcomes.domain.OutcomeEventsService;

import org.json.JSONObject;

abstract class OSOutcomeEventsClient implements OutcomeEventsService {

    final OneSignalAPIClient client;

    OSOutcomeEventsClient(OneSignalAPIClient client) {
        this.client = client;
    }

    public abstract void sendOutcomeEvent(JSONObject object, OneSignalApiResponseHandler responseHandler);

}
